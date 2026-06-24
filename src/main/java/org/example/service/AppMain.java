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
    private ToggleButton tsWorkingSet, tsSystemWs, tsStandby, tsModified, tsCache, tsAutoClean; // 内存清理模块开关
    private ToggleButton tsAutoStart; // ★ 开机自启动开关

    private final List<ToggleButton> allSwitches = new ArrayList<>();
    private final List<Label> allInfoIcons = new ArrayList<>();
    // ★ 统一收集所有卡片节标题、行主标题、行描述，主题切换时一次遍历全部更新
    private final List<VBox> allCards = new ArrayList<>();
    private final List<Label> allSectionTitles = new ArrayList<>();
    private final List<Label> allRowTitles = new ArrayList<>();  // 行主标题（粗体大字）
    private final List<Label> allRowDescs  = new ArrayList<>();  // 行描述（小字）
    private VBox logContainer, sidebar, mainContent, coreCard, restrictionCard, memoryCard, settingsCard;
    private ScrollPane logScroll;
    private Label title, panelTitle, sectionTitle, coreLabel, coreNum;
    private Label memSectionTitle, ramTitle, ramText, lblTimer, lblAuto;
    private Label settingsSectionTitle;
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
        // ★ 注册到统一集合
        allCards.add(restrictionCard);
        allSectionTitles.add(sectionTitle);

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

        // ★ 五个清理项（对标 Mem Reduct）
        // 前两项无需管理员，后三项需要管理员（会自动检测并在日志提示）
        RowResult m1 = createRow("🧹", "进程工作集", "强制回收各进程物理内存页", "无需管理员。将所有进程闲置的物理内存页挤入虚拟内存，立即释放 RAM，效果立竿见影。", true);
        RowResult m2 = createRow("🗄️", "文件系统缓存", "强行清空 Windows 备用缓存", "无需管理员（有时需要）。释放被系统文件和预加载占用的备用内存池。", true);
        RowResult m3 = createRow("⚙️", "系统工作集", "回收内核/驱动占用的内存", "需要管理员。释放 Windows 内核和驱动程序本身占用的物理内存。", false);
        RowResult m4 = createRow("⚡", "备用内存列表", "瞬间释放大量备用内存（最强）", "需要管理员。对标 Mem Reduct 核心功能：将 Standby 状态页面直接标为可用，可一次释放数 GB 内存。", false);
        RowResult m5 = createRow("💾", "修改页面写入", "脏页写盘后立即释放", "需要管理员。将被修改但未写入磁盘的内存页强制刷写，之后这些页面即可被回收。", false);
        tsWorkingSet = m1.toggle; tsCache = m2.toggle;
        tsSystemWs = m3.toggle; tsStandby = m4.toggle; tsModified = m5.toggle;

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
        rightControls.getChildren().addAll(m1.row, m2.row, m3.row, m4.row, m5.row, bottomControl);
        splitPane.getChildren().addAll(leftMonitor, rightControls);
        memoryCard.getChildren().addAll(memSectionTitle, splitPane);
        // ★ 注册到统一集合
        allCards.add(memoryCard);
        allSectionTitles.add(memSectionTitle);

        // ★ --- 模块 3：系统设置（开机自启动） ---
        settingsCard = new VBox(0);
        settingsCard.setStyle("-fx-background-color: #1a1d23; -fx-background-radius: 15; -fx-padding: 10 20;");
        settingsSectionTitle = new Label(" 🛠  系统设置");
        settingsSectionTitle.setStyle("-fx-text-fill: #666; -fx-font-size: 13px; -fx-padding: 15 0 10 0;");

        // 启动时读取注册表，同步真实状态（后台线程，避免阻塞 UI）
        boolean autoStartEnabled = false;
        try {
            autoStartEnabled = AutoStartService.isEnabled();
        } catch (Exception ignored) {}

        RowResult s1 = createRow("🚀", "开机自启动",
                "随 Windows 启动自动运行",
                "无风险。将程序注册到当前用户的启动项（注册表 HKCU Run），无需管理员权限，随时可关闭。",
                autoStartEnabled);
        tsAutoStart = s1.toggle;

        // ★ 监听开关变化，写入/删除注册表
        tsAutoStart.selectedProperty().addListener((obs, oldV, isOn) -> {
            new Thread(() -> {
                boolean success = isOn ? AutoStartService.enable() : AutoStartService.disable();
                Platform.runLater(() -> {
                    if (success) {
                        addLog("开机自启动 " + (isOn ? "已启用 ✅" : "已禁用"));
                    } else {
                        addLog("⚠️ 开机自启动设置失败，请检查日志");
                        tsAutoStart.setSelected(!isOn);
                    }
                });
            }).start();
        });

        settingsCard.getChildren().addAll(settingsSectionTitle, s1.row);
        // ★ 注册到统一集合
        allCards.add(settingsCard);
        allSectionTitles.add(settingsSectionTitle);

        // 将三个卡片全部加入主内容区
        mainContent.getChildren().addAll(topBar, restrictionCard, memoryCard, settingsCard);

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
        // ★ 注册进全局列表，主题切换时统一更新，不再依赖递归遍历
        allRowTitles.add(t);
        allRowDescs.add(d);

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
        String txtC      = isDarkTheme ? "white"    : "#1f2937";
        String dscC      = isDarkTheme ? "#666"     : "#4b5563";
        String cardBg    = isDarkTheme ? "#1a1d23"  : "#f9fafb";
        String cardBorder= isDarkTheme ? "transparent" : "#eee";
        String sidebarBg = isDarkTheme ? "#1a1d23"  : "#f3f4f6";
        String mainBg    = isDarkTheme ? "#0f1115"  : "#ffffff";
        String coreBg    = isDarkTheme ? "#252a34"  : "#ffffff";
        String inputBg   = isDarkTheme ? "#2d333b"  : "#ffffff";
        String inputBorder = isDarkTheme ? "#444"   : "#ccc";
        String btnBg     = isDarkTheme ? "#2d333b"  : "#e5e7eb";
        String ramBarBg  = isDarkTheme ? "#2d333b"  : "#e5e7eb";
        String infoColor = isDarkTheme ? "#555"     : "#999";

        // --- 容器背景 ---
        sidebar.setStyle("-fx-background-color: " + sidebarBg + ";");
        mainContent.setStyle("-fx-background-color: " + mainBg + ";");
        coreCard.setStyle("-fx-background-color: " + coreBg + "; -fx-background-radius: 12; -fx-padding: 20;");

        // --- 所有卡片（统一循环，新增卡片只需注册到 allCards/allSectionTitles 即可）---
        String cardStyle = "-fx-background-color: " + cardBg + "; -fx-background-radius: 15; -fx-padding: 10 20; -fx-border-color: " + cardBorder + ";";
        for (VBox card : allCards) card.setStyle(cardStyle);

        // --- 节标题（灰色小标，统一处理）---
        for (Label sec : allSectionTitles)
            sec.setStyle("-fx-text-fill: #666; -fx-font-size: 13px; -fx-padding: 15 0 10 0;");

        // --- 各卡片内行文字：直接遍历注册好的列表，100%可靠 ---
        for (Label lbl : allRowTitles) lbl.setStyle("-fx-text-fill: " + txtC + "; -fx-font-weight: bold; -fx-font-size: 17px;");
        for (Label lbl : allRowDescs)  lbl.setStyle("-fx-text-fill: " + dscC + "; -fx-font-size: 13px;");

        // --- 固定文字标签 ---
        title.setStyle("-fx-text-fill: " + txtC + ";");
        panelTitle.setStyle("-fx-text-fill: " + txtC + "; -fx-font-size: 26px; -fx-font-weight: bold;");
        coreLabel.setStyle("-fx-text-fill: #888;");
        ramTitle.setStyle("-fx-text-fill: " + txtC + "; -fx-font-size: 14px;");
        ramText.setStyle("-fx-text-fill: " + dscC + "; -fx-font-size: 12px;");
        lblTimer.setStyle("-fx-text-fill: " + dscC + ";");
        lblAuto.setStyle("-fx-text-fill: " + dscC + ";");

        // --- 输入框 ---
        txtTimer.setStyle("-fx-background-color: " + inputBg + "; -fx-text-fill: " + txtC
                + "; -fx-border-color: " + inputBorder + "; -fx-border-radius: 4;");

        // --- 进度条 ---
        ramBar.setStyle("-fx-accent: " + COLOR_PINK + "; -fx-control-inner-background: " + ramBarBg + ";");

        // --- 主题/GitHub 按钮 ---
        themeBtn.setText(isDarkTheme ? "🌙" : "☀️");
        themeBtn.setStyle("-fx-background-radius: 22; -fx-background-color: " + btnBg + "; -fx-text-fill: #f59e0b; -fx-cursor: hand;");
        githubBtn.setStyle("-fx-background-radius: 22; -fx-background-color: " + btnBg + "; -fx-cursor: hand;");

        // --- 所有 ⓘ 图标和开关 ---
        for (Label i : allInfoIcons)
            i.setStyle("-fx-text-fill: " + infoColor + "; -fx-font-size: 15px; -fx-cursor: hand;");
        for (ToggleButton s : allSwitches)
            s.setStyle("-fx-background-color: " + (s.isSelected() ? COLOR_BLUE : getOffColor()) + "; -fx-background-radius: 12;");

        // --- 日志区文字 ---
        for (Node n : logContainer.getChildren()) {
            if (n instanceof Label l) l.setStyle("-fx-text-fill: " + dscC + ";");
        }

        addLog("主题切换成功");
    }

    /**
     * 递归遍历容器内所有行（HBox），更新行标题和描述文字颜色。
     * 判断标准：HBox 的第二个子节点是 VBox（即 createRow 生成的 texts 容器）。
     * 递归确保 splitPane → rightControls 这类嵌套结构也能被正确处理。
     */
    private void updateCardTextColor(javafx.scene.Parent container, String txtC, String dscC) {
        for (Node n : container.getChildrenUnmodifiable()) {
            if (n instanceof HBox row
                    && row.getChildren().size() >= 2
                    && row.getChildren().get(1) instanceof VBox ts
                    && ts.getChildren().size() >= 2) {
                // 第一个子节点是 emoji Label，主题切换时颜色跟主文字保持一致
                if (row.getChildren().get(0) instanceof Label emoji) {
                    emoji.setStyle("-fx-text-fill: " + txtC + ";");
                }
                // texts VBox 里：第0个是标题，第1个是描述
                ((Label) ts.getChildren().get(0)).setStyle("-fx-text-fill: " + txtC + ";");
                ((Label) ts.getChildren().get(1)).setStyle("-fx-text-fill: " + dscC + ";");
            } else if (n instanceof javafx.scene.Parent p) {
                // 递归处理所有嵌套容器（HBox splitPane、VBox rightControls 等）
                updateCardTextColor(p, txtC, dscC);
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
        boolean doWs       = tsWorkingSet.isSelected();
        boolean doCache     = tsCache.isSelected();
        boolean doSystemWs  = tsSystemWs.isSelected();
        boolean doStandby   = tsStandby.isSelected();
        boolean doModified  = tsModified.isSelected();

        if (!doWs && !doCache && !doSystemWs && !doStandby && !doModified) {
            addLog("操作取消：未勾选任何清理项");
            return;
        }

        // 提前检测权限并给出提示
        boolean isAdmin = MemoryCleaner.isRunningAsAdmin();
        if ((doSystemWs || doStandby || doModified) && !isAdmin) {
            addLog("⚠️ 检测到非管理员权限，系统级清理项将被跳过");
        }

        addLog("正在执行深层系统内存清理...");
        new Thread(() -> {
            List<MemoryCleaner.CleanResult> results =
                    MemoryCleaner.executeClean(doWs, doSystemWs, doStandby, doModified, doCache);
            Platform.runLater(() -> {
                results.forEach(r -> addLog(r.toString()));
                addLog("— 清理完成 —");
            });
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
