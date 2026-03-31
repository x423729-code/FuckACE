package org.example.service; // 已经统一为你的文件夹实际路径

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import java.awt.Desktop;
import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class AppMain extends Application {
    private final SystemInfo systemInfo = new SystemInfo();
    private Timer monitorTimer;
    private Timer autoCleanTimer;

    private ToggleButton cpuAffinityCheck, priorityCheck, ecoModeCheck, ioWeightCheck, ramResidentCheck;
    private ToggleButton tsWorkingSet, tsCache, tsAutoClean; // 内存清理模块开关

    private final List<ToggleButton> allSwitches = new ArrayList<>();
    private final List<Label> allInfoIcons = new ArrayList<>();
    private VBox logContainer, sidebar, mainContent, coreCard, restrictionCard, memoryCard;
    private ScrollPane logScroll;
    private Label title, panelTitle, sectionTitle, coreLabel, coreNum;
    private Label memSectionTitle, ramTitle, ramText, lblTimer, lblAuto; // 内存模块文本
    private ProgressBar ramBar;
    private TextField txtTimer;
    private Button themeBtn, githubBtn, optimizeBtn, btnCleanNow;
    private boolean isDarkTheme = true;

    private final String COLOR_BLUE = "#3b82f6";
    private final String COLOR_PINK = "#ff0055"; // 火龙果色
    private String getOffColor() { return isDarkTheme ? "#3f444d" : "#d1d5db"; }

    @Override
    public void start(Stage stage) {
        // 1. 加载应用图标
        try {
            var logo = getClass().getResourceAsStream("/images/logo.png");
            if (logo != null) stage.getIcons().add(new Image(logo));
        } catch (Exception ignored) {}

        // 2. 初始化侧边栏
        sidebar = new VBox(25);
        sidebar.setPadding(new Insets(30, 20, 20, 20));
        sidebar.setPrefWidth(260);
        sidebar.setStyle("-fx-background-color: #1a1d23;");

        title = new Label("火龙果纸箱");
        title.getStyleClass().add("sidebar-title");
        title.setStyle("-fx-text-fill: white;");

        coreCard = new VBox(8);
        coreCard.setStyle("-fx-background-color: #252a34; -fx-background-radius: 12; -fx-padding: 20;");
        coreLabel = new Label("目标核心");
        coreLabel.getStyleClass().add("core-label");
        coreLabel.setStyle("-fx-text-fill: #888;");
        coreNum = new Label("#" + (Runtime.getRuntime().availableProcessors() - 1));
        coreNum.getStyleClass().add("core-number");
        coreNum.setStyle("-fx-text-fill: #3b82f6;");
        coreCard.getChildren().addAll(coreLabel, coreNum);

        Region spacerS = new Region(); VBox.setVgrow(spacerS, Priority.ALWAYS);

        logContainer = new VBox(5);
        logScroll = new ScrollPane(logContainer);
        logScroll.setFitToWidth(true);
        logScroll.setPrefHeight(180);
        logScroll.setStyle("-fx-background: transparent; -fx-background-color: rgba(15,17,21,0.4); -fx-background-radius: 10; -fx-padding: 10;");
        logScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        makeSmooth(logScroll);

        themeBtn = new Button("🌙");
        themeBtn.setMinSize(44, 44);
        themeBtn.setStyle("-fx-background-radius: 22; -fx-background-color: #2d333b; -fx-text-fill: #ffcf40; -fx-cursor: hand;");
        themeBtn.setOnAction(e -> toggleTheme());

        githubBtn = new Button();
        githubBtn.setMinSize(44, 44);
        githubBtn.setStyle("-fx-background-radius: 22; -fx-background-color: #2d333b; -fx-cursor: hand;");
        githubBtn.setOnAction(e -> { try { Desktop.getDesktop().browse(new URI("https://github.com/x423729-code/FuckACE")); } catch (Exception ignored) {} });
        try {
            var ghImg = getClass().getResourceAsStream("/images/github.png");
            if (ghImg != null) {
                ImageView iv = new ImageView(new Image(ghImg));
                iv.setFitWidth(22); iv.setFitHeight(22);
                githubBtn.setGraphic(iv);
            }
        } catch (Exception ignored) {}

        HBox bottomBtns = new HBox(15, themeBtn, githubBtn);
        bottomBtns.setAlignment(Pos.CENTER);
        sidebar.getChildren().addAll(title, coreCard, spacerS, logScroll, bottomBtns);

        // 3. 主内容区 (放入 ScrollPane 中以支持上下滑动)
        mainContent = new VBox(25);
        mainContent.setPadding(new Insets(30));
        mainContent.setStyle("-fx-background-color: #0f1115;");

        ScrollPane mainScrollPane = new ScrollPane(mainContent);
        mainScrollPane.setFitToWidth(true);
        mainScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        mainScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        mainScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        makeSmooth(mainScrollPane); // 给主面板也加上平滑滚动
        HBox.setHgrow(mainScrollPane, Priority.ALWAYS);

        panelTitle = new Label("控制面板");
        panelTitle.setStyle("-fx-text-fill: white; -fx-font-size: 26px; -fx-font-weight: bold;");
        optimizeBtn = new Button("▶  一键优化");
        optimizeBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 10 25; -fx-font-weight: bold; -fx-cursor: hand;");
        optimizeBtn.setOnAction(e -> executeOptimization());

        HBox topBar = new HBox(panelTitle, new Region(), optimizeBtn);
        HBox.setHgrow(topBar.getChildren().get(1), Priority.ALWAYS);
        topBar.setAlignment(Pos.CENTER_LEFT);

        // --- 模块 1：ACE 核心主动限制 ---
        restrictionCard = new VBox(0);
        restrictionCard.setStyle("-fx-background-color: #1a1d23; -fx-background-radius: 15; -fx-padding: 10 20;");
        sectionTitle = new Label(" ⚙  核心主动限制");
        sectionTitle.setStyle("-fx-text-fill: #666; -fx-font-size: 13px; -fx-padding: 15 0 10 0;");

        RowResult r1 = createRow("💠", "CPU 亲和性锁定", "强制绑定至最后一核", "极低风险。强制该进程仅能使用系统最后一个核心，减少对前台游戏的抢占。", false);
        RowResult r2 = createRow("⚡", "进程优先级压制", "设为空闲(Idle)级别", "低风险。当系统有其他高优先任务时，该进程会主动避让。", false);
        RowResult r3 = createRow("🍃", "Windows 效率模式", "系统级能耗限制(EcoQoS)", "低风险。Win11 特色功能，通过降低频率达到节能减负效果。", false);
        RowResult r4 = createRow("💾", "I/O 读写降权", "降低硬盘占用权重", "中风险。限制磁盘访问权重，防止因大流量读写引起卡顿。", false);
        RowResult r5 = createRow("🧠", "内存驻留降权", "降低RAM分配优先级", "中风险。在内存紧张时，系统会优先回收此进程的内存。", false);
        cpuAffinityCheck = r1.toggle; priorityCheck = r2.toggle; ecoModeCheck = r3.toggle; ioWeightCheck = r4.toggle; ramResidentCheck = r5.toggle;
        restrictionCard.getChildren().addAll(sectionTitle, r1.row, r2.row, r3.row, r4.row, r5.row);

        // --- 模块 2：系统内存清理 ---
        memoryCard = new VBox(0);
        memoryCard.setStyle("-fx-background-color: #1a1d23; -fx-background-radius: 15; -fx-padding: 10 20;");
        memSectionTitle = new Label(" 🚀 系统内存清理");
        memSectionTitle.setStyle("-fx-text-fill: #666; -fx-font-size: 13px; -fx-padding: 15 0 10 0;");

        HBox splitPane = new HBox(20);
        splitPane.setAlignment(Pos.CENTER_LEFT);

        VBox leftMonitor = new VBox(10);
        leftMonitor.setAlignment(Pos.CENTER);
        leftMonitor.setPrefWidth(250);
        ramTitle = new Label("系统内存占用率");
        ramTitle.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        ramBar = new ProgressBar(0);
        ramBar.setPrefWidth(200); ramBar.setPrefHeight(15);
        ramBar.setStyle("-fx-accent: " + COLOR_PINK + "; -fx-control-inner-background: #2d333b;");
        ramText = new Label("计算中...");
        ramText.setStyle("-fx-text-fill: #888; -fx-font-size: 12px;");
        leftMonitor.getChildren().addAll(ramTitle, ramBar, ramText);

        VBox rightControls = new VBox(0);
        HBox.setHgrow(rightControls, Priority.ALWAYS);

        RowResult m1 = createRow("🧹", "清理进程工作集", "将闲置内存挤压至虚拟内存", "大幅降低正在运行程序的物理内存占用，效果立竿见影。", true);
        RowResult m2 = createRow("🗄️", "清理系统文件缓存", "强行清空Windows备用缓存", "释放被系统文件和预加载占用的备用内存池。", true);
        tsWorkingSet = m1.toggle; tsCache = m2.toggle;

        HBox bottomControl = new HBox(15);
        bottomControl.setAlignment(Pos.CENTER_LEFT);
        bottomControl.setPadding(new Insets(15, 0, 15, 0));

        btnCleanNow = new Button("立即执行");
        btnCleanNow.setStyle("-fx-background-color: " + COLOR_PINK + "; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 6 15; -fx-font-weight: bold; -fx-cursor: hand;");
        btnCleanNow.setOnAction(e -> executeMemoryClean());

        lblTimer = new Label("定时(分):");
        lblTimer.setStyle("-fx-text-fill: #888;");
        txtTimer = new TextField("5");
        txtTimer.setPrefWidth(45);
        txtTimer.setStyle("-fx-background-color: #2d333b; -fx-text-fill: white; -fx-border-color: #444; -fx-border-radius: 4;");
        txtTimer.textProperty().addListener((obs, oldV, newV) -> {
            if (!newV.matches("\\d*")) txtTimer.setText(newV.replaceAll("[^\\d]", ""));
        });

        tsAutoClean = createSwitch("定时自动清理", false);
        lblAuto = new Label("自动清理");
        lblAuto.setStyle("-fx-text-fill: #888;");

        tsAutoClean.selectedProperty().addListener((obs, oldV, isAuto) -> {
            if (isAuto) {
                if (txtTimer.getText().isEmpty()) txtTimer.setText("5");
                startAutoClean(Integer.parseInt(txtTimer.getText()));
                txtTimer.setDisable(true);
            } else {
                stopAutoClean();
                txtTimer.setDisable(false);
            }
        });

        bottomControl.getChildren().addAll(btnCleanNow, lblTimer, txtTimer, tsAutoClean, lblAuto);
        rightControls.getChildren().addAll(m1.row, m2.row, bottomControl);
        splitPane.getChildren().addAll(leftMonitor, rightControls);
        memoryCard.getChildren().addAll(memSectionTitle, splitPane);

        mainContent.getChildren().addAll(topBar, restrictionCard, memoryCard);

        // 4. 加载场景 (注意这里将原来的 mainContent 替换为了支持滚动的 mainScrollPane)
        Scene scene = new Scene(new HBox(sidebar, mainScrollPane), 1000, 750);
        try { scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm()); }
        catch (Exception e) { System.err.println("CSS 加载失败！"); }

        stage.setScene(scene);
        stage.setTitle("火龙果纸箱 - Java Edition");
        stage.setOnCloseRequest(e -> stopTimers());

        startRamMonitor();
        stage.show();
    }

    private ToggleButton createSwitch(String logName, boolean defaultOn) {
        ToggleButton sw = new ToggleButton();
        sw.setPrefSize(44, 24);
        sw.setSelected(defaultOn);
        sw.setStyle("-fx-background-color: " + (defaultOn ? COLOR_BLUE : getOffColor()) + "; -fx-background-radius: 12; -fx-cursor: hand;");
        allSwitches.add(sw);

        StackPane thumb = new StackPane();
        thumb.setPrefSize(18, 18); thumb.setStyle("-fx-background-color: white; -fx-background-radius: 10;");
        thumb.setTranslateX(defaultOn ? 10 : -10);
        sw.setGraphic(thumb);

        sw.setOnAction(e -> {
            boolean on = sw.isSelected();
            new Timeline(new KeyFrame(Duration.millis(150), new KeyValue(thumb.translateXProperty(), on ? 10 : -10, Interpolator.EASE_BOTH))).play();
            sw.setStyle("-fx-background-color: " + (on ? COLOR_BLUE : getOffColor()) + "; -fx-background-radius: 12;");
            if (logName != null) addLog(logName + " " + (on ? "已开启" : "已禁用"));
        });
        return sw;
    }

    private RowResult createRow(String emoji, String tStr, String dStr, String tip, boolean defaultOn) {
        VBox texts = new VBox(2);
        Label t = new Label(tStr); t.getStyleClass().add("row-title"); t.setStyle("-fx-text-fill: white;");
        Label d = new Label(dStr); d.getStyleClass().add("row-desc"); d.setStyle("-fx-text-fill: #666;");
        texts.getChildren().addAll(t, d);

        Label info = new Label("ⓘ");
        info.setMinWidth(35); info.setAlignment(Pos.CENTER);
        info.setStyle("-fx-text-fill: #555; -fx-font-size: 15px; -fx-cursor: hand;");
        allInfoIcons.add(info);

        Popup popup = new Popup();
        info.setOnMouseEntered(e -> {
            Label pl = new Label(tip);
            pl.getStyleClass().add("tooltip-popup");
            pl.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: #444;",
                    isDarkTheme ? "#252a34" : "white", isDarkTheme ? "#eee" : "#333"));
            popup.getContent().setAll(pl);
            popup.show(info, info.localToScreen(0,0).getX() - 280, info.localToScreen(0,0).getY() + 30);
        });
        info.setOnMouseExited(e -> popup.hide());

        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        ToggleButton sw = createSwitch(tStr, defaultOn);

        HBox row = new HBox(12, new Label(emoji), texts, info, sp, sw);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(18, 0, 18, 0));
        row.setStyle("-fx-border-color: rgba(255,255,255,0.05); -fx-border-width: 0 0 1 0;");
        return new RowResult(row, sw);
    }

    private void toggleTheme() {
        isDarkTheme = !isDarkTheme;
        String txtC = isDarkTheme ? "white" : "#1f2937";
        String dscC = isDarkTheme ? "#666" : "#4b5563";
        String cardBg = isDarkTheme ? "#1a1d23" : "#f9fafb";
        String cardBorder = isDarkTheme ? "transparent" : "#eee";

        sidebar.setStyle("-fx-background-color: " + (isDarkTheme ? "#1a1d23" : "#f3f4f6") + ";");
        mainContent.setStyle("-fx-background-color: " + (isDarkTheme ? "#0f1115" : "#ffffff") + ";");

        restrictionCard.setStyle("-fx-background-color: " + cardBg + "; -fx-background-radius: 15; -fx-padding: 10 20; -fx-border-color: " + cardBorder + ";");
        memoryCard.setStyle("-fx-background-color: " + cardBg + "; -fx-background-radius: 15; -fx-padding: 10 20; -fx-border-color: " + cardBorder + ";");
        coreCard.setStyle("-fx-background-color: " + (isDarkTheme ? "#252a34" : "#ffffff") + "; -fx-background-radius: 12; -fx-padding: 20;");

        title.setStyle("-fx-text-fill: " + txtC + ";");
        panelTitle.setStyle("-fx-text-fill: " + txtC + "; -fx-font-size: 26px; -fx-font-weight: bold;");
        ramTitle.setStyle("-fx-text-fill: " + txtC + "; -fx-font-size: 14px;");

        ramText.setStyle("-fx-text-fill: " + dscC + "; -fx-font-size: 12px;");
        lblTimer.setStyle("-fx-text-fill: " + dscC + ";");
        lblAuto.setStyle("-fx-text-fill: " + dscC + ";");
        txtTimer.setStyle("-fx-background-color: " + (isDarkTheme ? "#2d333b" : "#ffffff") + "; -fx-text-fill: " + txtC + "; -fx-border-color: " + (isDarkTheme ? "#444" : "#ccc") + "; -fx-border-radius: 4;");
        ramBar.setStyle("-fx-accent: " + COLOR_PINK + "; -fx-control-inner-background: " + (isDarkTheme ? "#2d333b" : "#e5e7eb") + ";");

        themeBtn.setText(isDarkTheme ? "🌙" : "☀️");
        themeBtn.setStyle("-fx-background-radius: 22; -fx-background-color: " + (isDarkTheme ? "#2d333b" : "#e5e7eb") + "; -fx-text-fill: #f59e0b; -fx-cursor: hand;");

        for (Label i : allInfoIcons) i.setStyle("-fx-text-fill: " + (isDarkTheme ? "#555" : "#999") + "; -fx-font-size: 15px; -fx-cursor: hand;");
        for (ToggleButton s : allSwitches) s.setStyle("-fx-background-color: " + (s.isSelected() ? COLOR_BLUE : getOffColor()) + "; -fx-background-radius: 12;");

        updateCardTextColor(restrictionCard, txtC, dscC);
        updateCardTextColor(memoryCard, txtC, dscC);

        // 刷新现有的所有日志文本颜色
        for (Node n : logContainer.getChildren()) {
            if (n instanceof Label l) {
                l.setStyle("-fx-text-fill: " + dscC + ";");
            }
        }

        // 重新调用 addLog，现在它会自适应当前的主题色了
        addLog("主题切换成功");
    }

    private void updateCardTextColor(VBox card, String txtC, String dscC) {
        for (Node n : card.getChildren()) {
            if (n instanceof HBox row && row.getChildren().size() >= 2 && row.getChildren().get(1) instanceof VBox ts) {
                ((Label) ts.getChildren().get(0)).setStyle("-fx-text-fill: " + txtC + ";");
                ((Label) ts.getChildren().get(1)).setStyle("-fx-text-fill: " + dscC + ";");
            }
        }
    }

    private void makeSmooth(ScrollPane sp) {
        sp.addEventFilter(ScrollEvent.SCROLL, e -> {
            // 只有垂直滚动时才拦截
            if (e.getDeltaY() != 0) {
                e.consume(); // 吞掉原本过慢的默认事件

                // 👈 灵敏度调节阀！50 代表滚轮滚一下，画面移动 50 个像素。
                // 觉得慢了就调大（如 80），觉得太快就调小（如 30）
                double scrollSpeed = 50.0;

                double contentHeight = sp.getContent().getBoundsInLocal().getHeight();
                double viewportHeight = sp.getViewportBounds().getHeight();

                // 滚轮向上 getDeltaY() 是正数，向下是负数
                double direction = e.getDeltaY() > 0 ? -1 : 1;
                double newValue = sp.getVvalue() + (direction * scrollSpeed / (contentHeight - viewportHeight));

                sp.setVvalue(Math.max(0.0, Math.min(newValue, 1.0)));
            }
        });
    }

    private void addLog(String m) {
        Platform.runLater(() -> {
            Label l = new Label(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " " + m);
            l.getStyleClass().add("log-text");
            // 根据实时主题色赋予日志颜色
            l.setStyle("-fx-text-fill: " + (isDarkTheme ? "#666" : "#4b5563") + ";");
            logContainer.getChildren().add(l);
            new Timeline(new KeyFrame(Duration.millis(300), new KeyValue(logScroll.vvalueProperty(), 1.0, Interpolator.EASE_OUT))).play();
        });
    }

    private void executeOptimization() {
        // 这里需要引入你工程里其他的 Service 逻辑。
        addLog("正在扫描并优化进程...");
    }

    private void startRamMonitor() {
        monitorTimer = new Timer(true);
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        monitorTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                GlobalMemory memory = hal.getMemory();
                long total = memory.getTotal();
                long used = total - memory.getAvailable();
                double ratio = (double) used / total;
                String text = String.format("已用: %.1f GB / %.1f GB (%.0f%%)",
                        used / 1.073e9, total / 1.073e9, ratio * 100);

                Platform.runLater(() -> {
                    ramBar.setProgress(ratio);
                    ramText.setText(text);
                });
            }
        }, 0, 1000);
    }

    private void executeMemoryClean() {
        boolean doWs = tsWorkingSet.isSelected();
        boolean doCache = tsCache.isSelected();
        if (!doWs && !doCache) {
            addLog("操作取消：未勾选任何清理项");
            return;
        }
        addLog("正在执行深层系统内存清理...");
        new Thread(() -> {
            MemoryCleaner.executeClean(doWs, doCache);
            Platform.runLater(() -> addLog("✅ 内存清理完成"));
        }).start();
    }

    private void startAutoClean(int minutes) {
        stopAutoClean();
        autoCleanTimer = new Timer(true);
        long interval = (long) minutes * 60 * 1000;
        autoCleanTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                MemoryCleaner.executeClean(tsWorkingSet.isSelected(), tsCache.isSelected());
                Platform.runLater(() -> addLog("🔄 执行了一次自动内存清理"));
            }
        }, interval, interval);
    }

    private void stopAutoClean() {
        if (autoCleanTimer != null) {
            autoCleanTimer.cancel();
            autoCleanTimer = null;
        }
    }

    private void stopTimers() {
        if (monitorTimer != null) monitorTimer.cancel();
        stopAutoClean();
    }

    private static class RowResult {
        HBox row; ToggleButton toggle;
        RowResult(HBox r, ToggleButton t) { row = r; toggle = t; }
    }

    public static void main(String[] args) { launch(args); }
}