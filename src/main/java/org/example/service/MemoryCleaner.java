package org.example.service;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import com.sun.jna.platform.win32.WinNT.LUID;
import com.sun.jna.platform.win32.WinNT.TOKEN_PRIVILEGES;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.List;

/**
 * 完整内存清理器 —— 对标 Mem Reduct 全部清理项
 *
 * 清理能力分两档：
 *  【无需管理员】进程工作集、文件系统缓存
 *  【需要管理员】系统工作集、备用内存列表(Standby)、修改页面写入(Modified)
 *
 * 程序会在运行时自动检测权限，高权限项没有权限时会跳过并打印提示。
 */
public class MemoryCleaner {

    // =========================================================
    //  JNA 接口定义
    // =========================================================

    /** ntdll.dll —— 包含所有高级内存清理 API */
    private interface NtDll extends StdCallLibrary {
        NtDll INSTANCE = Native.load("ntdll", NtDll.class, W32APIOptions.DEFAULT_OPTIONS);

        /**
         * NtSetSystemInformation
         * @param SystemInformationClass  操作类型（见下方常量）
         * @param SystemInformation       参数指针
         * @param SystemInformationLength 参数大小
         * @return NTSTATUS (0 = 成功)
         */
        int NtSetSystemInformation(int SystemInformationClass,
                                   Pointer SystemInformation,
                                   int SystemInformationLength);
    }

    /** psapi.dll —— EmptyWorkingSet */
    private interface CustomPsapi extends StdCallLibrary {
        CustomPsapi INSTANCE = Native.load("psapi", CustomPsapi.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean EmptyWorkingSet(HANDLE hProcess);
    }

    /** kernel32.dll 扩展 —— SetSystemFileCacheSize */
    private interface Kernel32Ext extends StdCallLibrary {
        Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean SetSystemFileCacheSize(WinNT.SIZE_T MinimumFileCacheSize,
                                       WinNT.SIZE_T MaximumFileCacheSize,
                                       int Flags);
    }

    // =========================================================
    //  NtSetSystemInformation 操作类型常量（对标 Mem Reduct 源码）
    // =========================================================

    /** 系统内存列表操作（清理 Standby/Modified 等） */
    private static final int SystemMemoryListInformation = 80;

    /** 系统工作集清理 */
    private static final int SystemWorkingSetInformation = 1;

    /** MemoryPurgeLowPriorityStandbyList  —— 清理低优先级备用列表 */
    private static final int MemoryPurgeLowPriorityStandbyList = 5;

    /** MemoryPurgeStandbyList —— 清理全部备用列表（效果最强） */
    private static final int MemoryPurgeStandbyList = 4;

    /** MemoryFlushModifiedList —— 将修改页面写入磁盘并释放 */
    private static final int MemoryFlushModifiedList = 3;

    // =========================================================
    //  权限工具
    // =========================================================

    /**
     * 检测当前进程是否以管理员身份运行
     */
    public static boolean isRunningAsAdmin() {
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"cmd", "/c", "net session >nul 2>&1"});
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 为当前进程启用指定特权（如 SeIncreaseQuotaPrivilege）
     * 调用高级清理 API 前必须先提权，否则返回 ACCESS_DENIED
     *
     * @param privilegeName 特权名称字符串
     * @return 是否成功
     */
    private static boolean enablePrivilege(String privilegeName) {
        try {
            // OpenProcessToken 需要 HANDLEByReference，不是 HANDLE
            HANDLEByReference hToken = new HANDLEByReference();
            if (!Advapi32.INSTANCE.OpenProcessToken(
                    Kernel32.INSTANCE.GetCurrentProcess(),
                    WinNT.TOKEN_ADJUST_PRIVILEGES | WinNT.TOKEN_QUERY,
                    hToken)) {
                return false;
            }

            LUID luid = new LUID();
            if (!Advapi32.INSTANCE.LookupPrivilegeValue(null, privilegeName, luid)) {
                Kernel32.INSTANCE.CloseHandle(hToken.getValue());
                return false;
            }

            TOKEN_PRIVILEGES tp = new TOKEN_PRIVILEGES(1);
            tp.Privileges[0].Luid = luid;
            // Attributes 字段类型是 WinDef.DWORD，需要显式构造
            tp.Privileges[0].Attributes = new com.sun.jna.platform.win32.WinDef.DWORD(WinNT.SE_PRIVILEGE_ENABLED);

            boolean result = Advapi32.INSTANCE.AdjustTokenPrivileges(
                    hToken.getValue(), false, tp, tp.size(), null, new IntByReference());
            Kernel32.INSTANCE.CloseHandle(hToken.getValue());
            return result && Kernel32.INSTANCE.GetLastError() == 0;
        } catch (Exception e) {
            System.err.println("[MemoryCleaner] 提权失败: " + e.getMessage());
            return false;
        }
    }

    // =========================================================
    //  清理功能实现
    // =========================================================

    /**
     * 【无需管理员】清理所有进程工作集
     * 强制将各进程的物理内存页挤压到虚拟内存，立即释放 RAM
     */
    public static CleanResult cleanWorkingSets() {
        SystemInfo si = new SystemInfo();
        OperatingSystem os = si.getOperatingSystem();
        List<OSProcess> processes = os.getProcesses();

        int success = 0, fail = 0;
        for (OSProcess process : processes) {
            try {
                // PROCESS_SET_QUOTA = 0x0100
                HANDLE hProcess = Kernel32.INSTANCE.OpenProcess(0x0100, false, process.getProcessID());
                if (hProcess != null) {
                    if (CustomPsapi.INSTANCE.EmptyWorkingSet(hProcess)) success++;
                    else fail++;
                    Kernel32.INSTANCE.CloseHandle(hProcess);
                }
            } catch (Exception ignored) {}
        }
        return new CleanResult(true, String.format("进程工作集已清理（成功 %d 个，跳过 %d 个）", success, fail));
    }

    /**
     * 【需要管理员】清理系统工作集
     * 释放内核和驱动程序占用的工作集内存
     */
    public static CleanResult cleanSystemWorkingSet() {
        if (!isRunningAsAdmin()) {
            return new CleanResult(false, "系统工作集清理需要管理员权限，已跳过");
        }
        enablePrivilege("SeIncreaseQuotaPrivilege");

        // 传入 NULL + 0 即触发系统工作集清理
        int status = NtDll.INSTANCE.NtSetSystemInformation(
                SystemWorkingSetInformation, Pointer.NULL, 0);

        if (status == 0) {
            return new CleanResult(true, "系统工作集已清理");
        } else {
            return new CleanResult(false, String.format("系统工作集清理失败 NTSTATUS=0x%08X", status));
        }
    }

    /**
     * 【需要管理员】清理备用内存列表（Standby List）
     * 这是 Mem Reduct 效果最强的一项：将备用(Standby)页面直接标为可用
     * 可以瞬间释放几百 MB 到几 GB 的"已用"内存
     *
     * @param lowPriorityOnly true = 只清理低优先级备用列表（更安全），false = 全部清理（效果更强）
     */
    public static CleanResult cleanStandbyList(boolean lowPriorityOnly) {
        if (!isRunningAsAdmin()) {
            return new CleanResult(false, "备用内存列表清理需要管理员权限，已跳过");
        }
        enablePrivilege("SeProfileSingleProcessPrivilege");

        // 命令值写入一个 int，通过 Pointer 传递
        com.sun.jna.Memory cmd = new com.sun.jna.Memory(4);
        int command = lowPriorityOnly ? MemoryPurgeLowPriorityStandbyList : MemoryPurgeStandbyList;
        cmd.setInt(0, command);

        int status = NtDll.INSTANCE.NtSetSystemInformation(
                SystemMemoryListInformation, cmd, 4);

        if (status == 0) {
            return new CleanResult(true, lowPriorityOnly ? "低优先级备用列表已清理" : "备用内存列表已全部清理 ✅");
        } else {
            return new CleanResult(false, String.format("备用列表清理失败 NTSTATUS=0x%08X", status));
        }
    }

    /**
     * 【需要管理员】将修改页面写入磁盘
     * 把"已修改但未写盘"的脏页强制刷写，之后这些页面可被回收
     */
    public static CleanResult cleanModifiedPageList() {
        if (!isRunningAsAdmin()) {
            return new CleanResult(false, "修改页面写入需要管理员权限，已跳过");
        }
        enablePrivilege("SeProfileSingleProcessPrivilege");

        com.sun.jna.Memory cmd = new com.sun.jna.Memory(4);
        cmd.setInt(0, MemoryFlushModifiedList);

        int status = NtDll.INSTANCE.NtSetSystemInformation(
                SystemMemoryListInformation, cmd, 4);

        if (status == 0) {
            return new CleanResult(true, "修改页面已写入磁盘并释放");
        } else {
            return new CleanResult(false, String.format("修改页面写入失败 NTSTATUS=0x%08X", status));
        }
    }

    /**
     * 【无需管理员】清理文件系统缓存
     * 重置系统文件缓存大小上限，强制 Windows 收缩缓存池
     */
    public static CleanResult cleanFileSystemCache() {
        try {
            // 需要 SeIncreaseQuotaPrivilege，尝试提权（管理员下才会成功）
            enablePrivilege("SeIncreaseQuotaPrivilege");
            WinNT.SIZE_T minusOne = new WinNT.SIZE_T(-1);
            boolean ok = Kernel32Ext.INSTANCE.SetSystemFileCacheSize(minusOne, minusOne, 0);
            if (ok) {
                return new CleanResult(true, "文件系统缓存已清理");
            } else {
                return new CleanResult(false, "文件系统缓存清理失败（可能需要管理员权限）");
            }
        } catch (Exception e) {
            return new CleanResult(false, "文件系统缓存异常: " + e.getMessage());
        }
    }

    // =========================================================
    //  综合清理入口（供 UI 调用）
    // =========================================================

    /**
     * 执行综合清理，返回每一项的结果列表
     *
     * @param cleanWorkingSet    清理进程工作集
     * @param cleanSystemWs      清理系统工作集（需管理员）
     * @param cleanStandby       清理备用内存列表（需管理员，效果最强）
     * @param cleanModified      将修改页面写入磁盘（需管理员）
     * @param cleanCache         清理文件系统缓存
     */
    public static List<CleanResult> executeClean(
            boolean cleanWorkingSet,
            boolean cleanSystemWs,
            boolean cleanStandby,
            boolean cleanModified,
            boolean cleanCache) {

        List<CleanResult> results = new java.util.ArrayList<>();

        // 推荐顺序：先写盘 → 再清备用列表 → 再清工作集 → 最后清缓存
        // （与 Mem Reduct 源码顺序一致）
        if (cleanModified)    results.add(cleanModifiedPageList());
        if (cleanStandby)     results.add(cleanStandbyList(false));
        if (cleanSystemWs)    results.add(cleanSystemWorkingSet());
        if (cleanWorkingSet)  results.add(cleanWorkingSets());
        if (cleanCache)       results.add(cleanFileSystemCache());

        System.gc(); // 顺手回收 JVM 自身的内存
        return results;
    }

    /**
     * 兼容旧版调用（AppMain 中的原有调用方式）
     * 等同于只开进程工作集 + 文件缓存
     */
    public static void executeClean(boolean cleanWorkingSet, boolean cleanCache) {
        executeClean(cleanWorkingSet, false, false, false, cleanCache);
    }

    // =========================================================
    //  结果封装
    // =========================================================

    public static class CleanResult {
        public final boolean success;
        public final String message;

        public CleanResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        @Override
        public String toString() {
            return (success ? "✅ " : "⚠️ ") + message;
        }
    }
}
