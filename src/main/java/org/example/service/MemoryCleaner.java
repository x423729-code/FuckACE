package org.example.service;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.WinNT;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.List;

public class MemoryCleaner {

    // 扩展 Kernel32 以调用清空系统缓存的 API
    public interface Kernel32Ext extends Kernel32 {
        Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class);
        boolean SetSystemFileCacheSize(WinNT.SIZE_T MinimumFileCacheSize, WinNT.SIZE_T MaximumFileCacheSize, int Flags);
    }
    // 扩展 Psapi 以调用清空工作集的 API
    public interface PsapiExt extends com.sun.jna.platform.win32.Psapi {
        PsapiExt INSTANCE = Native.load("psapi", PsapiExt.class);
        boolean EmptyWorkingSet(WinNT.HANDLE hProcess);
    }
    /**
     * 清理所有进程的工作集 (Working Set)
     */
    public static void cleanWorkingSets() {
        SystemInfo si = new SystemInfo();
        OperatingSystem os = si.getOperatingSystem();
        List<OSProcess> processes = os.getProcesses();

        for (OSProcess process : processes) {
            try {
                // PROCESS_SET_QUOTA = 0x0100
                WinNT.HANDLE hProcess = Kernel32.INSTANCE.OpenProcess(0x0100, false, process.getProcessID());
                if (hProcess != null) {
                    PsapiExt.INSTANCE.EmptyWorkingSet(hProcess);
                    Kernel32.INSTANCE.CloseHandle(hProcess);
                }
            } catch (Exception e) {
                // 忽略权限不足的系统级进程
            }
        }
    }

    /**
     * 清理系统文件缓存 (System File Cache)
     */
    public static void cleanSystemCache() {
        try {
            WinNT.SIZE_T minusOne = new WinNT.SIZE_T(-1);
            Kernel32Ext.INSTANCE.SetSystemFileCacheSize(minusOne, minusOne, 0);
        } catch (Exception e) {
            System.err.println("清理系统缓存失败，可能需要以管理员身份运行");
        }
    }

    /**
     * 执行综合清理
     */
    public static void executeClean(boolean cleanWorkingSet, boolean cleanCache) {
        if (cleanWorkingSet) {
            cleanWorkingSets();
        }
        if (cleanCache) {
            cleanSystemCache();
        }
        System.gc(); // 顺手释放一下 Java 自己的内存
    }
}