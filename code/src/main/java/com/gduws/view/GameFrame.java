package com.gduws.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
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
import com.gduws.model.World;

/** 主窗口：加载关卡数据，组织布兵界面与战斗态切换。 */
public class GameFrame extends JFrame {

    private final GameStateManager stateManager = new GameStateManager();
    private final DeployController deploy;
    private final GamePanel gamePanel;
    private final GameLoop gameLoop;

    private final Map<String, JToggleButton> unitButtons = new LinkedHashMap<>();
    private final JLabel statusLabel = new JLabel();
    private JButton startButton;

    public GameFrame() {
        setTitle("GDUWS — Ghost Domains: Unmanned Warfare Sim");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // ---- 加载数据 ----
        UnitDefLoader unitDefs = new UnitDefLoader();
        LevelDef level;
        World world;
        try {
            unitDefs.loadDirectory(Paths.get("data", "units"));
            level = new LevelLoader().loadFile(Paths.get("data", "levels", "level_01.json"));
            GameMap map = new MapLoader().loadFile(Paths.get(level.mapPath));
            world = new World(map);
            placeEnemyUnits(world, level, unitDefs);
        } catch (IOException e) {
            throw new RuntimeException("加载关卡数据失败（请在 code/ 目录下运行）", e);
        }

        this.deploy = new DeployController(world, unitDefs, level);
        this.gamePanel = new GamePanel(world);
        gamePanel.addMouseListener(new InputHandler(deploy, stateManager, this::refreshSidebar, gamePanel));
        this.gameLoop = new GameLoop(world, stateManager, gamePanel::repaint);

        add(gamePanel, BorderLayout.CENTER);
        add(buildSidebar(level), BorderLayout.EAST);

        refreshSidebar();
        pack();
        setLocationRelativeTo(null);
    }

    private void placeEnemyUnits(World world, LevelDef level, UnitDefLoader unitDefs) {
        for (LevelDef.PlacedUnit p : level.enemyUnits) {
            UnitDef def = unitDefs.get(p.unitId);
            double cx = world.map.cellCenterX(p.col);
            double cy = world.map.cellCenterY(p.row);
            world.addUnit(new Unit(def, Faction.ENEMY, cx, cy));
        }
    }

    private JPanel buildSidebar(LevelDef level) {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        sidebar.setPreferredSize(new Dimension(240, gamePanel.getPreferredSize().height));

        JLabel title = new JLabel(level.name + "（布兵阶段）");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        sidebar.add(title);
        sidebar.add(Box.createVerticalStrut(4));
        sidebar.add(new JLabel("左键放置 · 右键移除"));
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(new JLabel("可用单位："));

        ButtonGroup group = new ButtonGroup();
        boolean first = true;
        for (String unitId : level.playerBudget.keySet()) {
            UnitDef def = deploy.defOf(unitId);
            JToggleButton btn = new JToggleButton();
            btn.setAlignmentX(LEFT_ALIGNMENT);
            btn.setMaximumSize(new Dimension(220, 30));
            btn.addActionListener(e -> {
                deploy.selectUnit(unitId);
                refreshSidebar();
            });
            if (first) {
                btn.setSelected(true);
                first = false;
            }
            group.add(btn);
            unitButtons.put(unitId, btn);
            sidebar.add(Box.createVerticalStrut(4));
            sidebar.add(btn);
        }

        sidebar.add(Box.createVerticalStrut(16));
        startButton = new JButton("开始战斗");
        startButton.setAlignmentX(LEFT_ALIGNMENT);
        startButton.setMaximumSize(new Dimension(220, 36));
        startButton.addActionListener(e -> startBattle());
        sidebar.add(startButton);

        sidebar.add(Box.createVerticalStrut(12));
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusLabel.setForeground(new Color(40, 40, 40));
        JPanel statusWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statusWrap.add(statusLabel);
        sidebar.add(statusWrap);

        sidebar.add(Box.createVerticalGlue());
        return sidebar;
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
        if (stateManager.is(GameState.DEPLOY)) {
            statusLabel.setText("<html>布兵中。" + safe(deploy.lastMessage()) + "</html>");
        } else if (stateManager.is(GameState.BATTLE)) {
            statusLabel.setText("<html><b>战斗已开始</b>。左键己方单位选中，再左键空地下达移动指令；右键取消。<br>"
                + "侦察单位移动靠近敌人时，已知敌情会显示为黄色 ? 标记。</html>");
        }
        gamePanel.repaint();
    }

    private void startBattle() {
        stateManager.setState(GameState.BATTLE);
        startButton.setEnabled(false);
        gameLoop.start();
        refreshSidebar();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
