package com.wkp.ticket;

import javafx.util.Pair;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class Main {
    private static final String[] ADDRESS = {"深圳北","广州南","韶关","郴州西","株洲西","长沙南"};

    public static void main(String[] args) {
        //站点列表
        List<String> address = new ArrayList<>();
        address.addAll(Arrays.asList(ADDRESS));
        //座位列表
        List<Integer> seat = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            seat.add(i);
        }
        //创建车次
        Ticket<String, Integer> ticket = new Ticket<>(address, seat);
        //设置座位列表改变监听
        ticket.setOnSeatChangedListener(new Ticket.OnSeatChangedListener<String, Integer>() {
            @Override
            public void onSeatChanged(ConcurrentMap<Pair<String, String>, List<Integer>> addressToSeat) {
                for (Map.Entry<Pair<String, String>, List<Integer>> entry : addressToSeat.entrySet()) {
                    Pair<String, String> key = entry.getKey();
                    List<Integer> value = entry.getValue();
                    //打印 起始站点---结束站点
                    System.out.println(key.getKey() + "---" + key.getValue() + ": ");
                    for (Integer integer : value) {
                        //打印 座位号
                        System.out.print(integer + ", ");
                    }
                    System.out.println();
                }
            }
        });
        Scanner scanner = new Scanner(System.in);
        Random random = new Random();
        System.out.println("----卖票系统----");
        System.out.print("始发站：");
        String startAddress = scanner.nextLine();
        System.out.print("终点站：");
        String endAddress = scanner.nextLine();
        while (!startAddress.equals("exit") && !endAddress.equals("exit")) {
            if (address.contains(startAddress) && address.contains(endAddress)) {
                //获取站点对应座位列表
                List<Integer> seats = ticket.getSeatsByAddress(startAddress, endAddress);
                if (seats.size() == 0) {
                    System.out.println("票已售罄！");
                }else {
                    int index = random.nextInt(seats.size());
                    //打印 即将售出座位号
                    System.out.println(seats.get(index));
                    //售出座位
                    ticket.sellTicket(startAddress, endAddress, seats.get(index));
                }
            }else {
                System.out.println("站点名录入错误，请重新录入！");
            }

            System.out.print("始发站：");
            startAddress = scanner.nextLine();
            System.out.print("终点站：");
            endAddress = scanner.nextLine();
        }
    }
}
