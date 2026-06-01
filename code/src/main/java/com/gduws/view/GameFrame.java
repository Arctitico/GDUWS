package com.gduws.view;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import com.gduws.audio.MusicPlayer;
import com.gduws.control.BattleSetup;
import com.gduws.control.DeployController;
import com.gduws.control.GameLoop;
import com.gduws.control.GameState;
import com.gduws.control.GameStateManager;
import com.gduws.data.LevelLoader;
import com.gduws.data.MapLoader;
import com.gduws.data.UnitDefLoader;
import com.gduws.model.Faction;
import com.gduws.model.GameMap;
import com.gduws.model.LevelDef;
import com.gduws.model.UnitDef;
import com.gduws.model.UnitRole;
import com.gduws.model.World;

/** 主窗口：串联 选关 → 布兵 → 战斗 → 结算 全流程 */
public class GameFrame extends JFrame {

    private final GameStateManager stateManager = new GameStateManager();
    private final UnitDefLoader unitDefs = new UnitDefLoader();
    private final BattleSetup battleSetup;
    private final List<LevelDef> levels = new ArrayList<>();
    private final MusicPlayer music = new MusicPlayer();


    // 以下随选关切换而重建
    private LevelDef level;
    private World world;
    private DeployController deploy;
    private GamePanel gamePanel;
    private GameLoop gameLoop;

    private final JPanel centerWrap = new JPanel(new BorderLayout());
    private final Map<String, JToggleButton> unitButtons = new LinkedHashMap<>();
    private final JLabel statusLabel = new JLabel();
    private final JLabel counterLabel = new JLabel();
    private JButton startButton;

    private final JPanel sidebarCards = new JPanel(new CardLayout());
    private static final String CARD_SELECT = "SELECT";
    private static final String CARD_DEPLOY = "DEPLOY";
    private static final String CARD_RESULT = "RESULT";
    private final JPanel deployCard = new JPanel();
    private final JLabel resultTitle = new JLabel();
    private final JLabel resultStats = new JLabel();

    public GameFrame(StartupDialog.Config config) {
        setTitle("GDUWS — Ghost Domains: Unmanned Warfare Sim");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        try {
            unitDefs.loadDirectory(Paths.get("data", "units"));
            loadAllLevels();
        } catch (IOException e) {
            throw new RuntimeException("加载关卡数据失败（请在 code/ 目录下运行）", e);
        }
        if (levels.isEmpty()) {
            throw new RuntimeException("未找到任何关卡（data/levels/*.json）");
        }
        this.battleSetup = new BattleSetup(unitDefs);

        centerWrap.setBackground(Color.BLACK);
        add(centerWrap, BorderLayout.CENTER);
        add(buildSidebar(), BorderLayout.EAST);

        showCard(CARD_SELECT);
        applyDisplayConfig(config);
        startMusic();
    }

    /** 装载并启动背景音乐，进入菜单场景；音乐不可用时静默跳过 */
    private void startMusic() {
        if (music.loadDefault()) {
            music.start();
            music.setScene(MusicPlayer.Scene.MENU);
        }
    }

    /** 扫描 data/levels 下全部 *.json 关卡，按文件名排序 */
    private void loadAllLevels() throws IOException {
        Path dir = Paths.get("data", "levels");
        LevelLoader loader = new LevelLoader();
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".json"))
                     .sorted()
                     .collect(Collectors.toList());
        }
        for (Path f : files) {
            levels.add(loader.loadFile(f));
        }
    }

    /** 按启动选择应用全屏或窗口显示模式 */
    private void applyDisplayConfig(StartupDialog.Config config) {
        if (config.fullscreen) {
            setUndecorated(true);
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
            setBounds(screen);
            installExitOnEscape();
        } else {
            setSize(config.width, config.height);
            setLocationRelativeTo(null);
        }
    }

    /** 全屏无边框时按 ESC 退出，避免玩家被困 */
    private void installExitOnEscape() {
        JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exitGame");
        root.getActionMap().put("exitGame", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                System.exit(0);
            }
        });
    }

    private JPanel buildSidebar() {
        sidebarCards.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        sidebarCards.setPreferredSize(new Dimension(260, 600));
        sidebarCards.add(buildSelectPanel(), CARD_SELECT);
        deployCard.setLayout(new BoxLayout(deployCard, BoxLayout.Y_AXIS));
        sidebarCards.add(deployCard, CARD_DEPLOY);
        sidebarCards.add(buildResultPanel(), CARD_RESULT);
        showCard(CARD_SELECT);
        return sidebarCards;
    }

    private JPanel buildSelectPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("选择关卡");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        p.add(title);
        p.add(Box.createVerticalStrut(12));

        for (LevelDef def : levels) {
            JButton btn = new JButton(def.name);
            btn.setAlignmentX(LEFT_ALIGNMENT);
            btn.setMaximumSize(new Dimension(240, 40));
            btn.addActionListener(e -> selectLevel(def));
            p.add(btn);
            p.add(Box.createVerticalStrut(8));
        }

        p.add(Box.createVerticalStrut(12));
        p.add(new JLabel("<html><i>选择关卡后进入布兵阶段</i></html>"));
        p.add(Box.createVerticalGlue());
        return p;
    }

    /** 选定关卡：重建地图、世界、画布、布兵控制器与主循环 */
    private void selectLevel(LevelDef def) {
        if (gameLoop != null) {
            gameLoop.stop();
        }
        this.level = def;
        try {
            GameMap map = new MapLoader().loadFile(Paths.get(def.mapPath));
            this.world = new World(map);
        } catch (IOException e) {
            throw new RuntimeException("加载关卡地图失败：" + def.mapPath, e);
        }
        this.deploy = new DeployController(world, unitDefs, level);
        this.gamePanel = new GamePanel(world);
        gamePanel.addMouseListener(new InputHandler(deploy, stateManager, this::refreshSidebar, gamePanel));
        this.gameLoop = new GameLoop(world, stateManager, this::onTick);
        gameLoop.setOnVictory(this::onVictory);

        centerWrap.removeAll();
        centerWrap.add(gamePanel, BorderLayout.CENTER);
        centerWrap.revalidate();
        centerWrap.repaint();

        rebuildDeployCard();
        enterDeploy();
    }

    /** 依据当前关卡的可用兵力重建布兵侧栏 */
    private void rebuildDeployCard() {
        deployCard.removeAll();
        unitButtons.clear();

        JLabel title = new JLabel(level.name);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        deployCard.add(title);
        deployCard.add(Box.createVerticalStrut(4));
        deployCard.add(new JLabel("左键放置 / 点击己方单位切换角色"));
        deployCard.add(new JLabel("右键移除"));
        deployCard.add(Box.createVerticalStrut(10));
        deployCard.add(new JLabel("可用单位："));

        ButtonGroup group = new ButtonGroup();
        boolean first = true;
        for (String unitId : level.playerBudget.keySet()) {
            JToggleButton btn = new JToggleButton();
            btn.setAlignmentX(LEFT_ALIGNMENT);
            btn.setMaximumSize(new Dimension(240, 30));
            btn.addActionListener(e -> { deploy.selectUnit(unitId); refreshSidebar(); });
            if (first) { btn.setSelected(true); first = false; }
            group.add(btn);
            unitButtons.put(unitId, btn);
            deployCard.add(Box.createVerticalStrut(4));
            deployCard.add(btn);
        }

        deployCard.add(Box.createVerticalStrut(12));
        deployCard.add(new JLabel("放置角色："));
        JPanel roleWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        roleWrap.setAlignmentX(LEFT_ALIGNMENT);
        roleWrap.setMaximumSize(new Dimension(240, 34));
        ButtonGroup roleGroup = new ButtonGroup();
        JToggleButton strikeBtn = new JToggleButton("打击");
        JToggleButton scoutBtn = new JToggleButton("侦察");
        strikeBtn.setSelected(true);
        strikeBtn.addActionListener(e -> { deploy.setDeployRole(UnitRole.STRIKE); refreshSidebar(); });
        scoutBtn.addActionListener(e -> { deploy.setDeployRole(UnitRole.SCOUT); refreshSidebar(); });
        roleGroup.add(strikeBtn);
        roleGroup.add(scoutBtn);
        roleWrap.add(strikeBtn);
        roleWrap.add(scoutBtn);
        deployCard.add(roleWrap);

        deployCard.add(Box.createVerticalStrut(16));
        startButton = new JButton("开始战斗");
        startButton.setAlignmentX(LEFT_ALIGNMENT);
        startButton.setMaximumSize(new Dimension(240, 36));
        startButton.addActionListener(e -> startBattle());
        deployCard.add(startButton);

        deployCard.add(Box.createVerticalStrut(10));
        counterLabel.setHorizontalAlignment(SwingConstants.LEFT);
        JPanel cWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        cWrap.add(counterLabel);
        deployCard.add(cWrap);

        deployCard.add(Box.createVerticalStrut(8));
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusLabel.setForeground(new Color(40, 40, 40));
        JPanel sWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        sWrap.add(statusLabel);
        deployCard.add(sWrap);

        deployCard.add(Box.createVerticalStrut(8));
        JButton back = new JButton("返回选关");
        back.setAlignmentX(LEFT_ALIGNMENT);
        back.setMaximumSize(new Dimension(240, 32));
        back.addActionListener(e -> backToSelect());
        deployCard.add(back);

        deployCard.add(Box.createVerticalGlue());
        deployCard.revalidate();
        deployCard.repaint();
    }

    private JPanel buildResultPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        resultTitle.setFont(resultTitle.getFont().deriveFont(Font.BOLD, 18f));
        p.add(resultTitle);
        p.add(Box.createVerticalStrut(10));
        p.add(resultStats);
        p.add(Box.createVerticalStrut(20));

        JButton retry = new JButton("重新挑战");
        retry.setAlignmentX(LEFT_ALIGNMENT);
        retry.setMaximumSize(new Dimension(240, 36));
        retry.addActionListener(e -> restartLevel());
        p.add(retry);

        p.add(Box.createVerticalStrut(8));
        JButton back = new JButton("返回选关");
        back.setAlignmentX(LEFT_ALIGNMENT);
        back.setMaximumSize(new Dimension(240, 36));
        back.addActionListener(e -> backToSelect());
        p.add(back);

        p.add(Box.createVerticalStrut(8));
        JButton exit = new JButton("退出");
        exit.setAlignmentX(LEFT_ALIGNMENT);
        exit.setMaximumSize(new Dimension(240, 36));
        exit.addActionListener(e -> System.exit(0));
        p.add(exit);

        p.add(Box.createVerticalGlue());
        return p;
    }

    private void showCard(String card) {
        ((CardLayout) sidebarCards.getLayout()).show(sidebarCards, card);
    }

    private void enterDeploy() {
        world.reset();
        battleSetup.placeEnemies(world, level);
        deploy.reset(level);
        stateManager.setState(GameState.DEPLOY);
        if (startButton != null) startButton.setEnabled(true);
        gamePanel.renderer().selectedUnit = null;
        music.setScene(MusicPlayer.Scene.MENU);
        showCard(CARD_DEPLOY);
        refreshSidebar();
    }

    private void restartLevel() {
        enterDeploy();
    }

    private void backToSelect() {
        gameLoop.stop();
        world.reset();
        stateManager.setState(GameState.LEVEL_SELECT);
        music.setScene(MusicPlayer.Scene.MENU);
        showCard(CARD_SELECT);
        gamePanel.repaint();
    }

    private void refreshSidebar() {
        String selected = deploy.selectedUnitId();
        for (Map.Entry<String, JToggleButton> e : unitButtons.entrySet()) {
            String unitId = e.getKey();
            UnitDef def = deploy.defOf(unitId);
            int left = deploy.remaining().getOrDefault(unitId, 0);
            e.getValue().setText(def.displayName + "  ×" + left);
            e.getValue().setEnabled(stateManager.is(GameState.DEPLOY));
            if (unitId.equals(selected)) {
                e.getValue().setSelected(true);
            }
        }
        int pAlive = world.countAlive(Faction.PLAYER);
        int eAlive = world.countAlive(Faction.ENEMY);
        counterLabel.setText("<html>剩余兵力：<br>己方 <b>" + pAlive + "</b> · 敌方 <b>" + eAlive + "</b></html>");
        if (stateManager.is(GameState.DEPLOY)) {
            statusLabel.setText("<html>布兵中。" + safe(deploy.lastMessage()) + "</html>");
        } else if (stateManager.is(GameState.BATTLE)) {
            statusLabel.setText("<html><b>战斗自动进行中</b>。一方损失 ≥90% 即结束。</html>");
        }
        gamePanel.repaint();
    }

    private void startBattle() {
        stateManager.setState(GameState.BATTLE);
        world.startBattle();
        startButton.setEnabled(false);
        gamePanel.renderer().selectedUnit = null;
        music.setScene(MusicPlayer.Scene.BATTLE);
        gameLoop.start();
        refreshSidebar();
    }

    /** 每 tick 回调：重绘战场并刷新侧栏，避免侧栏长期不重绘被外部浮窗遮盖 */
    private void onTick() {
        gamePanel.repaint();
        if (stateManager.is(GameState.BATTLE)) {
            int pAlive = world.countAlive(Faction.PLAYER);
            int eAlive = world.countAlive(Faction.ENEMY);
            counterLabel.setText("<html>剩余兵力：<br>己方 <b>" + pAlive + "</b> · 敌方 <b>" + eAlive + "</b></html>");
        }
        sidebarCards.repaint();
    }

    private void onVictory(Faction winner) {
        int p = world.countAlive(Faction.PLAYER);
        int en = world.countAlive(Faction.ENEMY);
        boolean playerWin = winner == Faction.PLAYER;
        resultTitle.setText(playerWin ? "胜利！" : "失败…");
        resultTitle.setForeground(playerWin ? new Color(40, 130, 40) : new Color(180, 40, 40));
        resultStats.setText("<html>己方剩余：" + p + " / " + world.initialCountOf(Faction.PLAYER)
            + "<br>敌方剩余：" + en + " / " + world.initialCountOf(Faction.ENEMY) + "</html>");
        music.setScene(MusicPlayer.Scene.MENU);
        showCard(CARD_RESULT);
        gamePanel.repaint();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
