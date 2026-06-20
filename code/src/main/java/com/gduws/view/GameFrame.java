package com.gduws.view;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
import javax.swing.Timer;
import javax.swing.ImageIcon;
import javax.imageio.ImageIO;

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
import com.gduws.model.Unit;
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
    private final JLabel fundsLabel = new JLabel();
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
        centerWrap.add(buildWelcomePanel(), BorderLayout.CENTER);
        add(centerWrap, BorderLayout.CENTER);

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

    private JPanel buildWelcomePanel() {
        ImageIcon gifIcon = null;
        File gifFile = new File("assets/welcome_background.gif");
        if (gifFile.exists()) {
            gifIcon = new ImageIcon(gifFile.getPath());
        }
        
        final ImageIcon icon = gifIcon;
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (icon != null) {
                    int panelWidth = getWidth();
                    int panelHeight = getHeight();
                    int imgWidth = icon.getIconWidth();
                    int imgHeight = icon.getIconHeight();
                    
                    double scale = Math.max((double) panelWidth / imgWidth, (double) panelHeight / imgHeight);
                    int scaledWidth = (int) (imgWidth * scale);
                    int scaledHeight = (int) (imgHeight * scale);
                    int x = (panelWidth - scaledWidth) / 2;
                    int y = (panelHeight - scaledHeight) / 2;
                    
                    g.drawImage(icon.getImage(), x, y, scaledWidth, scaledHeight, null);
                } else {
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        };
        
        if (icon != null) {
            Timer animTimer = new Timer(50, e -> panel.repaint());
            animTimer.start();
        }
        
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        
        panel.add(Box.createVerticalGlue());
        
        JLabel titleLabel = new JLabel("GDUWS");
        titleLabel.setFont(new Font("Dialog", Font.BOLD, 72));
        titleLabel.setForeground(new Color(70, 130, 220));
        titleLabel.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        panel.add(titleLabel);
        
        panel.add(Box.createVerticalStrut(20));
        
        JLabel subtitleLabel = new JLabel("Ghost Domains: Unmanned Warfare Sim");
        subtitleLabel.setFont(new Font("Dialog", Font.PLAIN, 24));
        subtitleLabel.setForeground(new Color(150, 150, 150));
        subtitleLabel.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        panel.add(subtitleLabel);
        
        panel.add(Box.createVerticalStrut(60));
        
        JButton startBtn = createTransparentButton("开始游戏", 200, 45);
        startBtn.addActionListener(e -> showLevelSelect());
        panel.add(startBtn);

        panel.add(Box.createVerticalStrut(15));

        JButton exitBtn = createTransparentButton("退出游戏", 200, 45);
        exitBtn.addActionListener(e -> System.exit(0));
        panel.add(exitBtn);
        
        panel.add(Box.createVerticalGlue());
        
        return panel;
    }
    
    private JButton createTransparentButton(String text, int width, int height) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                if (getModel().isRollover()) {
                    g2d.setColor(new Color(70, 130, 220, 180));
                } else {
                    g2d.setColor(new Color(30, 30, 30, 150));
                }
                
                g2d.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                
                if (getModel().isRollover()) {
                    g2d.setColor(new Color(100, 180, 255));
                    g2d.setStroke(new BasicStroke(2f));
                } else {
                    g2d.setColor(new Color(70, 130, 220, 200));
                    g2d.setStroke(new BasicStroke(1.5f));
                }
                g2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        
        btn.setFont(new Font("Dialog", Font.BOLD, 18));
        btn.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(width, height));
        btn.setForeground(Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        
        return btn;
    }
    
    private void showLevelSelect() {
        BufferedImage bgImage = null;
        try {
            bgImage = ImageIO.read(new File("assets/welcome_background.png"));
        } catch (IOException e) {
            System.err.println("无法加载背景图片: " + e.getMessage());
        }
        
        final BufferedImage background = bgImage;
        JPanel waitingPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (background != null) {
                    Graphics2D g2d = (Graphics2D) g;
                    int panelWidth = getWidth();
                    int panelHeight = getHeight();
                    int imgWidth = background.getWidth();
                    int imgHeight = background.getHeight();
                    
                    double scale = Math.max((double) panelWidth / imgWidth, (double) panelHeight / imgHeight);
                    int scaledWidth = (int) (imgWidth * scale);
                    int scaledHeight = (int) (imgHeight * scale);
                    int x = (panelWidth - scaledWidth) / 2;
                    int y = (panelHeight - scaledHeight) / 2;
                    
                    g2d.drawImage(background, x, y, scaledWidth, scaledHeight, null);
                } else {
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        };
        
        waitingPanel.setLayout(new BoxLayout(waitingPanel, BoxLayout.Y_AXIS));
        waitingPanel.setOpaque(false);
        waitingPanel.add(Box.createVerticalGlue());
        
        JLabel waitLabel = new JLabel("请从右侧选择关卡");
        waitLabel.setFont(new Font("Dialog", Font.BOLD, 32));
        waitLabel.setForeground(new Color(200, 200, 200));
        waitLabel.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        waitingPanel.add(waitLabel);
        
        waitingPanel.add(Box.createVerticalStrut(20));
        
        JLabel hintLabel = new JLabel("选择关卡后将进入部署阶段");
        hintLabel.setFont(new Font("Dialog", Font.PLAIN, 16));
        hintLabel.setForeground(new Color(150, 150, 150));
        hintLabel.setAlignmentX(JLabel.CENTER_ALIGNMENT);
        waitingPanel.add(hintLabel);
        
        waitingPanel.add(Box.createVerticalGlue());
        
        centerWrap.removeAll();
        centerWrap.add(waitingPanel, BorderLayout.CENTER);
        centerWrap.revalidate();
        centerWrap.repaint();
        
        if (getComponentCount() == 1) {
            add(buildSidebar(), BorderLayout.EAST);
        }
        
        showCard(CARD_SELECT);
        revalidate();
        repaint();
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
        InputHandler input = new InputHandler(deploy, stateManager, this::refreshSidebar, gamePanel);
        gamePanel.addMouseListener(input);
        gamePanel.addMouseMotionListener(input);
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
            UnitDef def = deploy.defOf(unitId);
            
            JPanel unitPanel = new JPanel();
            unitPanel.setLayout(new BoxLayout(unitPanel, BoxLayout.X_AXIS));
            unitPanel.setAlignmentX(LEFT_ALIGNMENT);
            unitPanel.setMaximumSize(new Dimension(240, 50));
            unitPanel.setOpaque(false);
            
            JLabel iconLabel = new JLabel();
            iconLabel.setPreferredSize(new Dimension(40, 40));
            ImageIcon icon = buildUnitIcon(def, 40);
            if (icon != null) {
                iconLabel.setIcon(icon);
            }
            unitPanel.add(iconLabel);
            unitPanel.add(Box.createHorizontalStrut(8));
            
            JToggleButton btn = new JToggleButton();
            btn.setAlignmentX(LEFT_ALIGNMENT);
            btn.setMaximumSize(new Dimension(190, 50));
            btn.addActionListener(e -> { deploy.selectUnit(unitId); refreshSidebar(); });
            if (first) { btn.setSelected(true); first = false; }
            group.add(btn);
            unitButtons.put(unitId, btn);
            
            unitPanel.add(btn);
            deployCard.add(Box.createVerticalStrut(4));
            deployCard.add(unitPanel);
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

        deployCard.add(Box.createVerticalStrut(12));
        JButton infoBtn = new JButton("兵种简介");
        infoBtn.setAlignmentX(LEFT_ALIGNMENT);
        infoBtn.setMaximumSize(new Dimension(240, 32));
        infoBtn.addActionListener(e -> showUnitInfoDialog());
        deployCard.add(infoBtn);

        deployCard.add(Box.createVerticalStrut(16));
        startButton = new JButton("开始战斗");
        startButton.setAlignmentX(LEFT_ALIGNMENT);
        startButton.setMaximumSize(new Dimension(240, 36));
        startButton.addActionListener(e -> startBattle());
        deployCard.add(startButton);

        deployCard.add(Box.createVerticalStrut(10));
        fundsLabel.setHorizontalAlignment(SwingConstants.LEFT);
        JPanel fWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        fWrap.add(fundsLabel);
        deployCard.add(fWrap);

        deployCard.add(Box.createVerticalStrut(4));
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
        gamePanel.renderer().selectedUnits.clear();
        gamePanel.renderer().overlayOnlySelected = false;
        gamePanel.renderer().fogMode = FogRenderer.Mode.DEPLOY;
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
        boolean funds = deploy.fundsMode();
        boolean inDeploy = stateManager.is(GameState.DEPLOY);
        for (Map.Entry<String, JToggleButton> e : unitButtons.entrySet()) {
            String unitId = e.getKey();
            UnitDef def = deploy.defOf(unitId);
            JToggleButton btn = e.getValue();
            if (funds) {
                // 资金制：不限数量，只显示价格；买不起则置灰
                btn.setText(def.displayName + "  ¥" + def.cost);
                btn.setEnabled(inDeploy && def.cost <= deploy.remainingFunds());
            } else {
                // 计数制：显示剩余数量
                int left = deploy.remaining().getOrDefault(unitId, 0);
                btn.setText(def.displayName + "  ×" + left);
                btn.setEnabled(inDeploy);
            }
            if (unitId.equals(selected)) {
                btn.setSelected(true);
            }
        }
        if (funds) {
            fundsLabel.setText("<html>资金：<b>" + deploy.remainingFunds() + "</b> / "
                + deploy.totalFunds() + "</html>");
            fundsLabel.setVisible(true);
        } else {
            fundsLabel.setVisible(false);
        }
        int pAlive = world.countAlive(Faction.PLAYER);
        int eAlive = world.countAlive(Faction.ENEMY);
        counterLabel.setText("<html>剩余兵力：<br>己方 <b>" + pAlive + "</b> · 敌方 <b>" + eAlive + "</b></html>");
        if (stateManager.is(GameState.DEPLOY)) {
            statusLabel.setText("<html>布兵中。" + safe(deploy.lastMessage()) + "</html>");
        } else if (stateManager.is(GameState.BATTLE)) {
            Set<Unit> sel = gamePanel.renderer().selectedUnits;
            if (sel.size() == 1) {
                Unit u = sel.iterator().next();
                String roleText = (u.role == UnitRole.SCOUT) ? "侦察 (Scout)" : "打击 (Strike)";
                statusLabel.setText("<html><b>" + u.def.displayName + "</b> · " + roleText
                    + "<br>HP: " + u.hp + " / " + u.def.maxHp + "</html>");
            } else {
                statusLabel.setText("<html><b>战斗自动进行中</b>。一方损失 ≥90% 即结束。</html>");
            }
        }
        gamePanel.repaint();
    }

    private void startBattle() {
        stateManager.setState(GameState.BATTLE);
        world.startBattle();
        startButton.setEnabled(false);
        gamePanel.renderer().selectedUnits.clear();
        gamePanel.renderer().overlayOnlySelected = true;
        gamePanel.renderer().fogMode = FogRenderer.Mode.BATTLE;
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
        resultTitle.setText(resultTitleText(playerWin, world.victoryReason()));
        resultTitle.setForeground(playerWin ? new Color(40, 130, 40) : new Color(180, 40, 40));
        resultStats.setText("<html>" + resultReasonText(playerWin, world.victoryReason())
            + "<br><br>己方剩余：" + p + " / " + world.initialCountOf(Faction.PLAYER)
            + "<br>敌方剩余：" + en + " / " + world.initialCountOf(Faction.ENEMY) + "</html>");
        gamePanel.renderer().fogMode = FogRenderer.Mode.NONE;
        music.setScene(MusicPlayer.Scene.MENU);
        showCard(CARD_RESULT);
        gamePanel.repaint();
    }

    /**
     * 生成布兵侧栏的兵种缩略图：底座 + 炮塔合成（与战场渲染一致——炮塔居中叠在底座之上），
     * 二者按同一比例缩放并居中填入 {@code size×size}。无底座贴图或加载失败时返回 null。
     */
    private ImageIcon buildUnitIcon(UnitDef def, int size) {
        if (def == null || def.spritePath == null) {
            return null;
        }
        try {
            File baseFile = new File(def.spritePath);
            if (!baseFile.exists()) {
                return null;
            }
            BufferedImage base = ImageIO.read(baseFile);
            if (base == null) {
                return null;
            }
            BufferedImage turret = null;
            if (def.turretSpritePath != null) {
                File turretFile = new File(def.turretSpritePath);
                if (turretFile.exists()) {
                    turret = ImageIO.read(turretFile);
                }
            }
            // 以底座与炮塔的最大边长为基准统一缩放，确保整体贴图（含外伸的炮管）不超出图标
            int maxDim = Math.max(base.getWidth(), base.getHeight());
            if (turret != null) {
                maxDim = Math.max(maxDim, Math.max(turret.getWidth(), turret.getHeight()));
            }
            double scale = (double) size / maxDim;

            BufferedImage icon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = icon.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            drawCentered(g2d, base, size, scale);
            if (turret != null) {
                drawCentered(g2d, turret, size, scale);
            }
            g2d.dispose();
            return new ImageIcon(icon);
        } catch (Exception ex) {
            return null; // 加载失败时不显示图标，不影响布兵
        }
    }

    /** 把 img 按 scale 缩放后居中绘制到 size×size 画布上 */
    private static void drawCentered(Graphics2D g2d, BufferedImage img, int size, double scale) {
        int w = (int) Math.round(img.getWidth() * scale);
        int h = (int) Math.round(img.getHeight() * scale);
        int x = (size - w) / 2;
        int y = (size - h) / 2;
        g2d.drawImage(img, x, y, w, h, null);
    }

    /** 兵种简介弹窗（FR-02 扩展）：列出本关可用兵种的关键属性，供布兵前查阅 */
    private void showUnitInfoDialog() {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        boolean funds = deploy.fundsMode();
        for (String unitId : level.playerBudget.keySet()) {
            UnitDef def = deploy.defOf(unitId);
            JLabel card = new JLabel(UnitInfoText.describeHtml(def, funds));
            card.setAlignmentX(LEFT_ALIGNMENT);
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(210, 210, 210)),
                BorderFactory.createEmptyBorder(6, 0, 8, 0)));
            list.add(card);
        }
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(list);
        scroll.setPreferredSize(new Dimension(360, 420));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        javax.swing.JDialog dialog = new javax.swing.JDialog(this, "兵种简介", true);
        dialog.getContentPane().add(scroll);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /** 依据胜负与结算原因给出更贴切的标题（FR-04） */
    private static String resultTitleText(boolean playerWin, com.gduws.model.VictoryReason reason) {
        if (reason == null) {
            return playerWin ? "胜利！" : "失败…";
        }
        switch (reason) {
            case ANNIHILATION:
                return playerWin ? "全面胜利！" : "全军覆没…";
            case ATTRITION:
                return playerWin ? "胜利！" : "失败…";
            case STALEMATE:
                return playerWin ? "战略胜利" : "战略失利";
            default:
                return playerWin ? "胜利！" : "失败…";
        }
    }

    /** 结算原因的一句话说明 */
    private static String resultReasonText(boolean playerWin, com.gduws.model.VictoryReason reason) {
        if (reason == null) {
            return playerWin ? "我方取得胜利。" : "我方落败。";
        }
        switch (reason) {
            case ANNIHILATION:
                return playerWin ? "敌方单位已被全部歼灭。" : "我方单位已被全部歼灭。";
            case ATTRITION:
                return playerWin ? "敌方损失超过 90%，已丧失战斗力。" : "我方损失超过 90%，已丧失战斗力。";
            case STALEMATE:
                return playerWin
                    ? "战场陷入僵持，按双方战损率判定——我方战损更低。"
                    : "战场陷入僵持，按双方战损率判定——我方战损更高。";
            default:
                return "";
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
