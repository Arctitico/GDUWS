package com.gduws.view;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

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
    private final LevelDef level;
    private final World world;
    private final DeployController deploy;
    private final BattleSetup battleSetup;
    private final GamePanel gamePanel;
    private final GameLoop gameLoop;

    private final Map<String, JToggleButton> unitButtons = new LinkedHashMap<>();
    private final JLabel statusLabel = new JLabel();
    private final JLabel counterLabel = new JLabel();
    private JButton startButton;

    private final JPanel sidebarCards = new JPanel(new CardLayout());
    private static final String CARD_SELECT = "SELECT";
    private static final String CARD_DEPLOY = "DEPLOY";
    private static final String CARD_RESULT = "RESULT";
    private final JLabel resultTitle = new JLabel();
    private final JLabel resultStats = new JLabel();

    public GameFrame() {
        setTitle("GDUWS — Ghost Domains: Unmanned Warfare Sim");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        try {
            unitDefs.loadDirectory(Paths.get("data", "units"));
            level = new LevelLoader().loadFile(Paths.get("data", "levels", "level_01.json"));
            GameMap map = new MapLoader().loadFile(Paths.get(level.mapPath));
            world = new World(map);
        } catch (IOException e) {
            throw new RuntimeException("加载关卡数据失败（请在 code/ 目录下运行）", e);
        }

        this.deploy = new DeployController(world, unitDefs, level);
        this.battleSetup = new BattleSetup(unitDefs);
        this.gamePanel = new GamePanel(world);
        gamePanel.addMouseListener(new InputHandler(deploy, stateManager, this::refreshSidebar, gamePanel));
        this.gameLoop = new GameLoop(world, stateManager, gamePanel::repaint);
        gameLoop.setOnVictory(this::onVictory);

        add(gamePanel, BorderLayout.CENTER);
        add(buildSidebar(), BorderLayout.EAST);

        refreshSidebar();
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildSidebar() {
        sidebarCards.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        sidebarCards.setPreferredSize(new Dimension(260, gamePanel.getPreferredSize().height));
        sidebarCards.add(buildSelectPanel(), CARD_SELECT);
        sidebarCards.add(buildDeployPanel(), CARD_DEPLOY);
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

        JButton btn = new JButton(level.name);
        btn.setAlignmentX(LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(240, 40));
        btn.addActionListener(e -> enterDeploy());
        p.add(btn);

        p.add(Box.createVerticalStrut(20));
        p.add(new JLabel("<html><i>选择关卡后进入布兵阶段</i></html>"));
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel buildDeployPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(level.name);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        p.add(title);
        p.add(Box.createVerticalStrut(4));
        p.add(new JLabel("左键放置 / 点击己方单位切换角色"));
        p.add(new JLabel("右键移除"));
        p.add(Box.createVerticalStrut(10));
        p.add(new JLabel("可用单位："));

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
            p.add(Box.createVerticalStrut(4));
            p.add(btn);
        }

        p.add(Box.createVerticalStrut(12));
        p.add(new JLabel("放置角色："));
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
        p.add(roleWrap);

        p.add(Box.createVerticalStrut(16));
        startButton = new JButton("开始战斗");
        startButton.setAlignmentX(LEFT_ALIGNMENT);
        startButton.setMaximumSize(new Dimension(240, 36));
        startButton.addActionListener(e -> startBattle());
        p.add(startButton);

        p.add(Box.createVerticalStrut(10));
        counterLabel.setHorizontalAlignment(SwingConstants.LEFT);
        JPanel cWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        cWrap.add(counterLabel);
        p.add(cWrap);

        p.add(Box.createVerticalStrut(8));
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusLabel.setForeground(new Color(40, 40, 40));
        JPanel sWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        sWrap.add(statusLabel);
        p.add(sWrap);

        p.add(Box.createVerticalGlue());
        return p;
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
        gameLoop.start();
        refreshSidebar();
    }

    private void onVictory(Faction winner) {
        int p = world.countAlive(Faction.PLAYER);
        int en = world.countAlive(Faction.ENEMY);
        boolean playerWin = winner == Faction.PLAYER;
        resultTitle.setText(playerWin ? "胜利！" : "失败…");
        resultTitle.setForeground(playerWin ? new Color(40, 130, 40) : new Color(180, 40, 40));
        resultStats.setText("<html>己方剩余：" + p + " / " + world.initialCountOf(Faction.PLAYER)
            + "<br>敌方剩余：" + en + " / " + world.initialCountOf(Faction.ENEMY) + "</html>");
        showCard(CARD_RESULT);
        gamePanel.repaint();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
