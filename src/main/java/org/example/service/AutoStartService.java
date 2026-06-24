package org.example.service;

import java.io.IOException;

/**
 * 开机自启动服务
 * 通过操作 Windows 注册表 HKCU\Software\Microsoft\Windows\CurrentVersion\Run 实现
 * 无需管理员权限（HKCU 是当前用户键，写入不需要提权）
 */
public class AutoStartService {

    // 注册表路径和应用名称（Key 名称）
    private static final String REG_PATH = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String APP_NAME = "PitayaBox";

    /**
     * 获取当前程序的可执行文件路径
     * 在打包为 exe 后会返回 exe 的绝对路径
     * 开发调试时返回的是 java 进程路径（行为正常，不影响测试）
     */
    private static String getExePath() {
        return ProcessHandle.current()
                .info()
                .command()
                .orElse(null);
    }

    /**
     * 查询当前是否已设置开机自启动
     *
     * @return true = 已启用，false = 未启用或查询失败
     */
    public static boolean isEnabled() {
        try {
            // reg query 命令：查询指定注册表项
            Process p = Runtime.getRuntime().exec(new String[]{
                    "reg", "query", REG_PATH, "/v", APP_NAME
            });
            int exitCode = p.waitFor();
            return exitCode == 0; // 退出码 0 表示找到了该项
        } catch (Exception e) {
            System.err.println("[AutoStart] 查询注册表失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 启用开机自启动（写入注册表）
     *
     * @return true = 成功，false = 失败（如路径获取失败）
     */
    public static boolean enable() {
        String exePath = getExePath();
        if (exePath == null || exePath.isBlank()) {
            System.err.println("[AutoStart] 无法获取程序路径，自启动设置失败");
            return false;
        }

        try {
            // reg add 命令：添加/覆盖注册表字符串值
            // /f 表示强制覆盖，不弹确认框
            Process p = Runtime.getRuntime().exec(new String[]{
                    "reg", "add", REG_PATH,
                    "/v", APP_NAME,
                    "/t", "REG_SZ",
                    "/d", "\"" + exePath + "\"",
                    "/f"
            });
            int exitCode = p.waitFor();
            if (exitCode == 0) {
                System.out.println("[AutoStart] 已启用开机自启动: " + exePath);
                return true;
            } else {
                System.err.println("[AutoStart] 注册表写入失败，退出码: " + exitCode);
                return false;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("[AutoStart] 写入注册表异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 禁用开机自启动（删除注册表项）
     *
     * @return true = 成功（或本来就不存在），false = 失败
     */
    public static boolean disable() {
        try {
            // reg delete 命令：删除注册表值
            // /f 表示不弹确认框
            Process p = Runtime.getRuntime().exec(new String[]{
                    "reg", "delete", REG_PATH,
                    "/v", APP_NAME,
                    "/f"
            });
            int exitCode = p.waitFor();
            // 退出码 0 成功，1 表示项不存在（也视为成功，因为目标状态已达到）
            if (exitCode == 0 || exitCode == 1) {
                System.out.println("[AutoStart] 已禁用开机自启动");
                return true;
            } else {
                System.err.println("[AutoStart] 注册表删除失败，退出码: " + exitCode);
                return false;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("[AutoStart] 删除注册表异常: " + e.getMessage());
            return false;
        }
    }
}
