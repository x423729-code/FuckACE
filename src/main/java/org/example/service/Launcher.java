package org.example.service;

public class Launcher {
    public static void main(String[] args) {
        // 这一步是关键：由一个不继承 Application 的类来启动
        org.example.service.AppMain.main(args);
    }
}