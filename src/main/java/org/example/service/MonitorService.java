package org.example.service;

import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MonitorService {
    private final SystemInfo si = new SystemInfo();
    private final OperatingSystem os = si.getOperatingSystem();

    // 定义我们要监控或限制的目标黑名单（对标原项目的进程雷达）
    private static final List<String> BLACKLIST = Arrays.asList(
            "SGuard64.exe",
            "SGuardSvc64.exe",
            "SGuardSvc.exe",
            "ACE-Base.exe"
    );

    /**
     * 获取当前系统中正在运行的“黑名单”进程
     * 用于 UI 界面左侧的“进程雷达”显示
     */
    public List<ProcessInfo> getRunningBlacklistProcesses() {
        List<OSProcess> processes = os.getProcesses();
        return processes.stream()
                .filter(p -> BLACKLIST.contains(p.getName()))
                .map(p -> new ProcessInfo(
                        p.getProcessID(),
                        p.getName(),
                        // 计算 CPU 占用率 (OSHI 返回的是总占用百分比)
                        100d * (p.getKernelTime() + p.getUserTime()) / p.getUpTime(),
                        p.getResidentSetSize() / 1024.0 / 1024.0 // 转为 MB
                ))
                .collect(Collectors.toList());
    }

    /**
     * 获取系统逻辑核心数，用于计算目标核心（Total - 1）
     * 对标 Rust 的 find_target_core
     */


    // 内部类：用于封装 UI 展示所需的简易进程信息
    // ... 前面是 getRunningBlacklistProcesses 等方法

    // 必须确保这个类在 MonitorService 的最后一个 } 之前
    public static class ProcessInfo {
        public final int pid;
        public final String name;
        public final double cpuUsage;
        public final double memoryMb;

        public ProcessInfo(int pid, String name, double cpuUsage, double memoryMb) {
            this.pid = pid;
            this.name = name;
            this.cpuUsage = cpuUsage;
            this.memoryMb = memoryMb;
        }
    }
} // 这是 MonitorService 类的结束大括号