package com.gduws.testkit;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.gduws.control.BattleSetup;
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
import com.gduws.view.GamePanel;
import com.gduws.view.GameRenderer;

/**
 * 可视化测试场景回放器（测试的可选「看得见」模式）。
 *
 * <p>用与集成测试相同的确定性场景构造 {@link World}，复用游戏自身的 {@link GameRenderer}/{@link GamePanel}
 * 在窗口中逐 tick 渲染，让人亲眼看着战斗推演与 AI 行为跑一遍；底部状态栏实时显示与测试断言对应的状态
 * （情报共享、双方存活、胜者）。</p>
 *
 * <ul>
 *   <li>{@code java ... com.gduws.testkit.ScenarioViewer}            打开可视化窗口（默认）</li>
 *   <li>{@code java ... com.gduws.testkit.ScenarioViewer --selftest} 无界面自检：离屏渲染各场景，校验渲染链路不崩</li>
 * </ul>
 *
 * <p>注意：本类仅用于「演示/观察」，断言验证仍以无界面的 JUnit 套件为准。</p>
 */
public final class ScenarioViewer {

    private static final int MAX_TICKS = 12000;

    // ---------- 场景定义 ----------

    private record Scenario(String name, Supplier<World> builder, Function<World, String> status) { }

    private static String zh(Faction f) {
        return f == Faction.PLAYER ? "玩家" : "敌方";
    }

    private static String battleStatus(World w) {
        String win = w.winner() != null ? "　|　胜者：" + zh(w.winner()) : "";
        return String.format("tick %d　玩家存活 %d　敌方存活 %d%s",
            w.tickCount(), w.countAlive(Faction.PLAYER), w.countAlive(Faction.ENEMY), win);
    }

    /** 与集成测试一致的场景集合（数据缺失时自动省略真实关卡场景）。 */
    private static List<Scenario> scenarios() {
        List<Scenario> list = new ArrayList<>();

        // 场景 1：侦察发现 → 情报共享 → 打击据情报奔向视野外的敌人（对应 ReconStrikeChainIT）
        list.add(new Scenario("侦察 → 情报共享 → 打击接敌（协同链路）", () -> {
            World w = new World(Fixtures.landMap(40, 20));
            Unit scout = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 29, 10);
            scout.role = UnitRole.SCOUT;
            w.addUnit(scout);
            w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 5, 10)); // 远处打击兵
            w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.ENEMY, 30, 10));
            w.startBattle();
            return w;
        }, w -> String.format("情报板：%s　|　%s",
            w.intelOf(Faction.PLAYER).hasAnyEnemy() ? "已共享敌情 ✓" : "未发现",
            battleStatus(w))));

        // 场景 2：8 v 2 完整自动战斗推演（对应 FullBattleIT）
        list.add(new Scenario("完整自动战斗 8 v 2", () -> {
            World w = new World(Fixtures.landMap(25, 25));
            Unit scout = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 2, 12);
            scout.role = UnitRole.SCOUT;
            w.addUnit(scout);
            int[][] cells = {{2, 11}, {2, 13}, {3, 11}, {3, 12}, {3, 13}, {2, 14}, {3, 14}};
            for (int[] c : cells) {
                w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, c[0], c[1]));
            }
            w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.ENEMY, 21, 12));
            w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.ENEMY, 22, 12));
            w.startBattle();
            return w;
        }, ScenarioViewer::battleStatus));

        // 场景 3：90% 损失判定 - 玩家存活率 5%
        list.add(new Scenario("胜负判定：90% 损失 - 玩家败", () -> {
            World w = new World(Fixtures.landMap(30, 30));
            for (int i = 0; i < 20; i++) {
                Unit u = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 5 + i/4, 10 + i%4);
                w.addUnit(u);
                if (i < 19) u.hp = 0;
            }
            for (int i = 0; i < 15; i++) {
                w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.ENEMY, 25, 10 + i));
            }
            w.startBattle();
            return w;
        }, w -> battleStatus(w) + String.format("　|　玩家损失率：%.0f%%",
            (1.0 - (double)w.countAlive(Faction.PLAYER) / w.initialCountOf(Faction.PLAYER)) * 100)));

        // 场景 4：僵持超时判定
        list.add(new Scenario("胜负判定：僵持超时 - 双方隔离", () -> {
            World w = Fixtures.separatedWorld(5, 5);
            w.startBattle();
            return w;
        }, w -> battleStatus(w) + String.format("　|　僵持 %d/2500 tick", w.tickCount())));

        // 场景 5：打击单位空闲转侦察
        list.add(new Scenario("AI 行为：打击单位 IDLE 90 tick 转侦察", () -> {
            World w = new World(Fixtures.landMap(30, 30));
            Unit u = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 15, 15);
            u.role = UnitRole.STRIKE;
            w.addUnit(u);
            return w;
        }, w -> {
            Unit u = w.units.get(0);
            return String.format("tick %d　|　角色：%s　|　状态：%s", w.tickCount(),
                u.role == UnitRole.SCOUT ? "侦察 ✓" : "打击", u.state);
        }));

        // 场景 6：射弹飞行与命中
        list.add(new Scenario("战斗系统：射弹飞行轨迹与命中", () -> {
            World w = new World(Fixtures.landMap(30, 20));
            w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 5, 10));
            w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.ENEMY, 12, 10));
            w.startBattle();
            return w;
        }, w -> String.format("%s　|　射弹数：%d", battleStatus(w), w.projectiles.size())));

        // 场景 7：多单位协同包围
        list.add(new Scenario("战术场景：多单位协同包围", () -> {
            World w = new World(Fixtures.landMap(35, 35));
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                int cx = 17 + (int)(8 * Math.cos(angle));
                int cy = 17 + (int)(8 * Math.sin(angle));
                Unit u = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, cx, cy);
                u.role = (i == 0) ? UnitRole.SCOUT : UnitRole.STRIKE;
                w.addUnit(u);
            }
            w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.ENEMY, 17, 17));
            w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.ENEMY, 18, 17));
            w.startBattle();
            return w;
        }, ScenarioViewer::battleStatus));

        // 场景 8：非对称兵力对抗
        list.add(new Scenario("战术场景：3 v 10 非对称战", () -> {
            World w = new World(Fixtures.landMap(40, 25));
            for (int i = 0; i < 3; i++) {
                Unit u = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 5, 10 + i * 2);
                u.role = UnitRole.SCOUT;
                w.addUnit(u);
            }
            for (int i = 0; i < 10; i++) {
                w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.ENEMY, 35, 8 + i));
            }
            w.startBattle();
            return w;
        }, ScenarioViewer::battleStatus));

        // 场景 9：残骸生成验证
        list.add(new Scenario("渲染测试：单位阵亡残骸生成", () -> {
            World w = new World(Fixtures.landMap(25, 25));
            for (int i = 0; i < 6; i++) {
                Unit u = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 8, 10 + i);
                w.addUnit(u);
                if (i < 3) u.hp = 0;
            }
            for (int i = 0; i < 6; i++) {
                Unit u = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.ENEMY, 17, 10 + i);
                w.addUnit(u);
                if (i < 2) u.hp = 0;
            }
            w.startBattle();
            w.tick();
            return w;
        }, w -> String.format("%s　|　残骸：%d", battleStatus(w), w.wreckages.size())));

        // 场景 10：大规模寻路压力测试
        list.add(new Scenario("性能测试：15 v 15 大规模寻路", () -> {
            World w = new World(Fixtures.landMap(50, 50));
            for (int i = 0; i < 15; i++) {
                Unit u = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 5, 10 + i * 2);
                u.role = (i % 3 == 0) ? UnitRole.SCOUT : UnitRole.STRIKE;
                w.addUnit(u);
            }
            for (int i = 0; i < 15; i++) {
                w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.ENEMY, 45, 10 + i * 2));
            }
            w.startBattle();
            return w;
        }, ScenarioViewer::battleStatus));

        // 场景 11：真实关卡 level_01（真实地图/贴图，对应 DataDrivenLoadIT 的数据），仅在能定位到 data/ 时加入
        Path data = Fixtures.dataRoot();
        if (data != null) {
            list.add(new Scenario("真实关卡 level_01 推演（真实地图与贴图）",
                () -> realLevel(data), ScenarioViewer::battleStatus));
        }
        return list;
    }

    /** 加载真实 level_01：敌方按预置放入，玩家在左侧镜像放一支同等兵力，再开战。 */
    private static World realLevel(Path data) {
        try {
            UnitDefLoader defs = new UnitDefLoader();
            defs.loadDirectory(data.resolve("units"));
            LevelDef level = new LevelLoader().loadFile(data.resolve("levels/level_01.json"));
            GameMap map = new MapLoader().loadFile(data.resolve("maps/level_01.map"));
            World w = new World(map);
            new BattleSetup(defs).placeEnemies(w, level);

            UnitDef tank = defs.get("light_tank");
            int i = 0;
            for (LevelDef.PlacedUnit e : level.enemyUnits) {
                Point cell = map.findNearestPassable(5, e.row, tank.movementType, 6);
                if (cell != null) {
                    Unit u = new Unit(tank, Faction.PLAYER, map.cellCenterX(cell.x), map.cellCenterY(cell.y));
                    u.role = (i % 3 == 0) ? UnitRole.SCOUT : UnitRole.STRIKE; // 每 3 个带 1 个侦察引导发现
                    w.addUnit(u);
                }
                i++;
            }
            w.startBattle();
            return w;
        } catch (Exception ex) {
            // 数据异常时回退到一个最简场景，保证回放器不崩
            World w = new World(Fixtures.landMap(20, 20));
            w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 3, 10));
            w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.ENEMY, 16, 10));
            w.startBattle();
            return w;
        }
    }

    // ---------- 入口 ----------

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--selftest")) {
            selfTest();
            return;
        }
        SwingUtilities.invokeLater(() -> new ScenarioViewer().show());
    }

    /** 无界面自检：离屏渲染每个场景若干 tick，验证构建与渲染链路不抛异常（CI/无显示器可用）。 */
    private static void selfTest() {
        List<Scenario> all = scenarios();
        GameRenderer renderer = new GameRenderer();
        for (Scenario s : all) {
            World w = s.builder().get();
            for (int i = 0; i < 200 && w.winner() == null; i++) {
                w.tick();
            }
            BufferedImage img = new BufferedImage(
                Math.max(1, w.map.pixelWidth()), Math.max(1, w.map.pixelHeight()), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            renderer.render(g, w);
            g.dispose();
            System.out.println("[OK] " + s.name() + "  ->  " + s.status().apply(w));
        }
        System.out.println("selftest passed: " + all.size() + " 个场景渲染正常");
    }

    // ---------- GUI 控制 ----------

    private List<Scenario> scenes;
    private int idx;
    private World world;
    private int ticks;
    private GamePanel panel;
    private JLabel status;
    private Timer timer;
    private JFrame frame;

    private void show() {
        scenes = scenarios();
        frame = new JFrame("GDUWS 测试场景可视化回放器");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        JButton prev = new JButton("◀ 上一个");
        JButton next = new JButton("下一个 ▶");
        JButton restart = new JButton("重新开始");
        JToggleButton pause = new JToggleButton("暂停");
        JComboBox<String> speed = new JComboBox<>(new String[]{"0.5x", "1x", "2x", "4x"});
        speed.setSelectedIndex(1);
        bar.add(prev);
        bar.add(next);
        bar.add(restart);
        bar.add(pause);
        bar.add(new JLabel("  速度"));
        bar.add(speed);
        frame.add(bar, BorderLayout.NORTH);

        status = new JLabel(" ");
        status.setFont(new Font("Monospaced", Font.PLAIN, 14));
        status.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        frame.add(status, BorderLayout.SOUTH);

        timer = new Timer(33, e -> step());

        prev.addActionListener(e -> { idx = (idx - 1 + scenes.size()) % scenes.size(); load(); });
        next.addActionListener(e -> { idx = (idx + 1) % scenes.size(); load(); });
        restart.addActionListener(e -> load());
        pause.addActionListener(e -> {
            if (pause.isSelected()) { timer.stop(); pause.setText("继续"); }
            else { timer.start(); pause.setText("暂停"); }
        });
        int[] delays = {66, 33, 16, 8};
        speed.addActionListener(e -> timer.setDelay(delays[speed.getSelectedIndex()]));

        idx = 0;
        load();
        frame.setSize(1000, 800);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /** 载入当前场景：重建世界与画布，复位计时。 */
    private void load() {
        Scenario s = scenes.get(idx);
        world = s.builder().get();
        ticks = 0;
        if (panel != null) {
            frame.remove(panel);
        }
        panel = new GamePanel(world);          // 复用游戏画布：自带缩放/平移，自动贴合地图
        panel.renderer().showOverlay = true;   // 显示己方寻路路径与已知敌情标记
        frame.add(panel, BorderLayout.CENTER);
        frame.revalidate();
        frame.repaint();
        updateStatus();
        timer.start();
    }

    /** 每帧推进一 tick（产生胜者或达上限后停推，仅保留渲染）。 */
    private void step() {
        if (world.winner() == null && ticks < MAX_TICKS) {
            world.tick();
            ticks++;
        }
        panel.repaint();
        updateStatus();
    }

    private void updateStatus() {
        Scenario s = scenes.get(idx);
        status.setText(String.format("【场景 %d/%d】%s　|　%s",
            idx + 1, scenes.size(), s.name(), s.status().apply(world)));
    }
}
