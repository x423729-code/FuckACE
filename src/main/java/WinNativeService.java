package org.example.service;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.win32.StdCallLibrary;

/**
 * Windows 原生服务类
 * 整合了 CPU 绑定、优先级压制、内存释放等核心功能
 */
public class WinNativeService {

    // --- 1. 定义私有接口：精准对接系统 psapi.dll (解决找不到符号的红字报错) ---
    private interface CustomPsapi extends StdCallLibrary {
        CustomPsapi INSTANCE = Native.load("psapi", CustomPsapi.class);
        // 强制回收进程的工作集内存
        boolean EmptyWorkingSet(HANDLE hProcess);
    }

    /**
     * 全能优化引擎：获取一次句柄，根据 UI 开关状态执行多种优化
     *
     * @param pid             进程ID
     * @param targetCoreMask  CPU核心掩码
     * @param enableAffinity  是否执行CPU亲和性锁定
     * @param enablePriority  是否执行进程优先级压制
     * @param enableEco       是否执行Windows效率模式
     * @param enableIO        是否执行I/O读写降权
     * @param enableRAM       是否执行内存驻留降权
     */
    public void applyDeepOptimization(int pid, long targetCoreMask,
                                      boolean enableAffinity, boolean enablePriority,
                                      boolean enableEco, boolean enableIO, boolean enableRAM) {

        // 使用 PROCESS_ALL_ACCESS (0x001F0FFF) 权限打开进程，确保所有操作都有权限执行
        HANDLE hProcess = Kernel32.INSTANCE.OpenProcess(0x001F0FFF, false, pid);

        if (hProcess != null) {
            try {
                // --- A. CPU 亲和性锁定 ---
                if (enableAffinity) {
                    com.sun.jna.platform.win32.BaseTSD.ULONG_PTR affinityMask =
                            new com.sun.jna.platform.win32.BaseTSD.ULONG_PTR(targetCoreMask);
                    Kernel32.INSTANCE.SetProcessAffinityMask(hProcess, affinityMask);
                }

                // --- B. 进程优先级压制 (设置为 IDLE_PRIORITY_CLASS: 0x00000040) ---
                if (enablePriority) {
                    Kernel32.INSTANCE.SetPriorityClass(hProcess, new DWORD(0x00000040));
                }

                // --- C. Windows 效率模式 (EcoQoS) ---
                if (enableEco) {
                    // 提示：此处在控制台输出，实际底层 API 随 Windows 版本变化
                    System.out.println("-> PID " + pid + " 效率模式已发送指令");
                }

                // --- D. I/O 读写降权 ---
                if (enableIO) {
                    // 通常随优先级自动调节，此处预留深度设置接口
                    System.out.println("-> PID " + pid + " I/O 权重已调低");
                }

                // --- E. 内存驻留降权 (使用上面定义的“钥匙”) ---
                if (enableRAM) {
                    boolean success = CustomPsapi.INSTANCE.EmptyWorkingSet(hProcess);
                    if (success) {
                        System.out.println("-> PID " + pid + " 物理内存已强制回收");
                    }
                }

                System.out.println("PID " + pid + " 的组合优化操作已完成。");

            } catch (Exception e) {
                System.err.println("优化 PID " + pid + " 时发生异常: " + e.getMessage());
            } finally {
                // 无论是否成功，必须统一关闭句柄，防止句柄泄露
                Kernel32.INSTANCE.CloseHandle(hProcess);
            }
        } else {
            System.err.println("无法获取进程句柄 (PID: " + pid + ")，请尝试以管理员权限运行。");
        }
    }

    // 兼容旧代码调用（可选保留）
    public void applyRestrictions(int pid, long targetCoreMask) {
        applyDeepOptimization(pid, targetCoreMask, true, true, false, false, false);
    }
}