package com.wkp.ticket;

import com.sun.istack.internal.NotNull;
import javafx.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 车票售卖类
 * <p>
 *     车票售卖，每两个站点对应区间对应不同的座位列表，存储Map映射{@link #mAddressToSeat}，实例对象时必须给入站点集合{@link #mAddresses}，
 *     站点集合传入后不再改变；座位集合支持创建对象时传入，也支持删减{@link #removeSeat(Object)}、增加{@link #addSeat(Object)}；最主要方法
 *     卖票{@link #sellTicket(Object, Object, Object)}、获取对应站点座位列表{@link #getSeatsByAddress(Object, Object)}、获取所有站点对应
 *     座位列表的映射{@link #getAddressToSeat()}；设置座位列表改变监听{@link #setOnSeatChangedListener(OnSeatChangedListener)}，方便同步
 *     更新。
 * </p>
 * @param <A> 站点泛型
 * @param <S> 座位泛型
 */
public class Ticket<A, S> {

    private static final int FLAG_ADD_SEAT = 0;     //添加座位
    private static final int FLAG_REMOVE_SEAT = 1;  //移除座位
    private static final int FLAG_CLEAR_SEAT = 2;   //清空座位
    private static final int FLAG_SELL_SEAT = 3;    //卖出座位
    private List<A> mAddresses;
    private List<S> mSeats;
    private ConcurrentMap<Pair<A, A>, List<S>> mAddressToSeat;
    private OnSeatChangedListener<A, S> mOnSeatChangedListener;

    public Ticket(@NotNull List<A> addresses) {
        this(addresses, new ArrayList<>());
    }

    public Ticket(@NotNull List<A> addresses, @NotNull List<S> seats) {
        mAddresses = addresses;
        mSeats = seats;
        initAddress();
    }

    /**
     * 初始化站点对应座位列表映射
     */
    private synchronized void initAddress() {
        mAddressToSeat = new ConcurrentHashMap<>();
        for (int i = 0; i < mAddresses.size() - 1; i++) {
            for (int j = i + 1; j < mAddresses.size(); j++) {
                List<S> seat = new ArrayList<>();
                seat.addAll(mSeats);
                mAddressToSeat.put(new Pair<>(mAddresses.get(i), mAddresses.get(j)), seat);
            }
        }
    }

    /**
     * 座位列表变化通知
     * @param flag  标记
     * @param index 索引
     * @param seat  座位
     * @return
     */
    private synchronized boolean notifySeatChanged(int flag,int index,S seat,int startAddress,int endAddress) {
        boolean result = false;
        switch (flag) {
            case FLAG_ADD_SEAT:
                result = true;
                for (Map.Entry<Pair<A, A>, List<S>> entry : mAddressToSeat.entrySet()) {
                    entry.getValue().add(index,seat);
                }
                break;
            case FLAG_REMOVE_SEAT:
                result = true;
                for (Map.Entry<Pair<A, A>, List<S>> entry : mAddressToSeat.entrySet()) {
                    if (entry.getValue().contains(seat)) {
                        entry.getValue().remove(seat);
                    }
                }
                break;
            case FLAG_CLEAR_SEAT:
                result = true;
                for (Map.Entry<Pair<A, A>, List<S>> entry : mAddressToSeat.entrySet()) {
                    entry.getValue().clear();
                }
                break;
            case FLAG_SELL_SEAT:
                result = true;
                A start = mAddresses.get(startAddress);
                A end = mAddresses.get(endAddress);
                for (Map.Entry<Pair<A, A>, List<S>> entry : mAddressToSeat.entrySet()) {
                    Pair<A, A> key = entry.getKey();
                    if (befContainAft(start, end, key.getKey(), key.getValue()) || befContainAft(key.getKey(), key.getValue(), start, end)) {
                        entry.getValue().remove(seat);
                    }
                }
                break;
                default:
                    result = false;
                    break;
        }
        if (result) {
            if (mOnSeatChangedListener != null) {
                mOnSeatChangedListener.onSeatChanged(mAddressToSeat);
            }
        }
        return result;
    }

    /**
     * 获取站点索引
     *
     * @param address 站点
     * @return
     */
    public int indexOfAddress(A address) {
        return mAddresses.indexOf(address);
    }


    /**
     * 是否包含该站点
     *
     * @param address 站点
     * @return
     */
    public boolean containAddress(A address) {
        return mAddresses.contains(address);
    }

    /**
     * 是否包含该座位
     *
     * @param seat 座位
     * @return
     */
    public boolean containSeat(S seat) {
        return mSeats.contains(seat);
    }

    /**
     * 添加座位
     *
     * @param seat 座位
     * @return
     */
    public synchronized boolean addSeat(S seat) {
        return !mSeats.contains(seat) && mSeats.add(seat) && notifySeatChanged(FLAG_ADD_SEAT,mSeats.size(),seat,-1,-1);
    }

    /**
     * 添加座位
     *
     * @param index 索引
     * @param seat  座位
     * @return
     */
    public synchronized boolean addSeat(int index, S seat) {
        if (mSeats.contains(seat)) {
            return false;
        }
        mSeats.add(index, seat);
        notifySeatChanged(FLAG_ADD_SEAT, index, seat,-1,-1);
        return true;
    }

    /**
     * 获取座位索引
     *
     * @param seat 座位
     * @return
     */
    public int indexOfSeat(S seat) {
        return mSeats.indexOf(seat);
    }

    /**
     * 移除座位
     *
     * @param seat 座位
     * @return
     */
    public synchronized boolean removeSeat(S seat) {
        return mSeats.remove(seat) && notifySeatChanged(FLAG_REMOVE_SEAT,-1,seat,-1,-1);
    }

    /**
     * 清空座位
     */
    public synchronized void clearSeat() {
        mAddresses.clear();
        notifySeatChanged(FLAG_CLEAR_SEAT, -1, null,-1,-1);
    }

    /**
     * 卖票
     *
     * @param startAddress 开始站点索引
     * @param endAddress   结束站点索引
     * @param seat         座位索引
     * @return
     */
    private boolean sellTicket(int startAddress, int endAddress, int seat) throws IllegalArgumentException {
        if (startAddress < 0 || startAddress >= mAddresses.size()) {
            throw new IllegalArgumentException("The startAddress less than zero or out of rang: " + startAddress);
        }
        if (endAddress < 0 || endAddress >= mAddresses.size()) {
            throw new IllegalArgumentException("The endAddress less than zero or out of rang: " + endAddress);
        }
        if (seat < 0 || seat >= mSeats.size()) {
            throw new IllegalArgumentException("The seat less than zero or out of rang: " + seat);
        }
        return notifySeatChanged(FLAG_SELL_SEAT,-1,mSeats.get(seat),startAddress,endAddress);
    }

    /**
     * 卖票
     * @param startAddress  开始站点
     * @param endAddress    结束站点
     * @param seat          座位
     * @return
     */
    public boolean sellTicket(A startAddress, A endAddress, S seat) {
        return sellTicket(mAddresses.indexOf(startAddress), mAddresses.indexOf(endAddress), mSeats.indexOf(seat));
    }

    /**
     * 获取座位列表
     * @param startAddress  开始站点
     * @param endAddress    结束站点
     * @return
     */
    public List<S> getSeatsByAddress(A startAddress, A endAddress) {
        for (Map.Entry<Pair<A, A>, List<S>> entry : mAddressToSeat.entrySet()) {
            Pair<A, A> pair = entry.getKey();
            if (startAddress.equals(pair.getKey()) && endAddress.equals(pair.getValue())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 获取站点对应的座位列表
     * @return
     */
    public ConcurrentMap<Pair<A, A>, List<S>> getAddressToSeat() {
        return mAddressToSeat;
    }

    /**
     * 前区间是否包含后区间
     *
     * @param befStart 前区间开始索引
     * @param befEnd   前区间结束索引
     * @param aftStart 后区间开始索引
     * @param aftEnd   后区间结束索引
     * @return
     * @throws IllegalArgumentException
     */
    private boolean befContainAft(int befStart, int befEnd, int aftStart, int aftEnd) throws IllegalArgumentException {
        if (befStart < 0 || befStart >= mAddresses.size()) {
            throw new IllegalArgumentException("The befStart less than zero or out of rang: " + befStart);
        }
        if (befEnd < 0 || befEnd >= mAddresses.size()) {
            throw new IllegalArgumentException("The befEnd less than zero or out of rang: " + befEnd);
        }
        if (befEnd < 0 || befEnd >= mAddresses.size()) {
            throw new IllegalArgumentException("The aftStart less than zero or out of rang: " + aftStart);
        }
        if (aftEnd < 0 || aftEnd >= mAddresses.size()) {
            throw new IllegalArgumentException("The aftEnd less than zero or out of rang: " + aftEnd);
        }
        if (befStart >= befEnd) {
            throw new IllegalArgumentException("The befEnd " + befEnd + " less than the befStart " + befStart);
        }
        if (aftStart >= aftEnd) {
            throw new IllegalArgumentException("The aftEnd " + aftEnd + " less than the aftStart " + aftStart);
        }
        return befStart <= aftStart && befEnd > aftStart;
    }

    /**
     * 前区间是否包含后区间
     *
     * @param befStart 前区间开始站点
     * @param befEnd   前区间结束站点
     * @param aftStart 后区间开始站点
     * @param aftEnd   后区间结束站点
     * @return
     * @throws IllegalArgumentException
     */
    public boolean befContainAft(A befStart, A befEnd, A aftStart, A aftEnd) throws IllegalArgumentException {
        return befContainAft(mAddresses.indexOf(befStart), mAddresses.indexOf(befEnd), mAddresses.indexOf(aftStart), mAddresses.indexOf(aftEnd));
    }

    /**
     * 座位列表变化监听
     * @param <A>
     * @param <S>
     */
    public interface OnSeatChangedListener<A,S>{
        /**
         * 座位列表改变监听
         * @param addressToSeat 站点对应的座位列表，始站点、末站点、座位列表
         */
        void onSeatChanged(ConcurrentMap<Pair<A, A>, List<S>> addressToSeat);
    }

    /**
     * 设置座位列表改变监听
     * @param listener  监听器
     */
    public void setOnSeatChangedListener(OnSeatChangedListener<A, S> listener) {
        mOnSeatChangedListener = listener;
    }
}
