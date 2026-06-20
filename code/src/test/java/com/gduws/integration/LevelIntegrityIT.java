package com.gduws.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.data.LevelLoader;
import com.gduws.data.MapLoader;
import com.gduws.data.UnitDefLoader;
import com.gduws.model.Faction;
import com.gduws.model.GameMap;
import com.gduws.model.LevelDef;
import com.gduws.model.MovementType;
import com.gduws.model.Unit;
import com.gduws.model.UnitDef;

/**
 * 集成测试：扫描 {@code data/levels} 下的全部关卡，对每一关做"设计自洽性"不变式校验。
 *
 * <p>这些不变式保证手写关卡数据可玩、可胜，避免后续编辑悄悄破坏某一关：</p>
 * <ul>
 *   <li><b>敌方落地</b>：每个敌方预置单位所在格，对其移动域必须可通行
 *       （陆军在陆地、海/潜在水域、空军任意），否则单位会被卡死。</li>
 *   <li><b>可胜性</b>：玩家可用兵种的攻击域，必须覆盖敌方出现的每一个高度层；
 *       否则存在永远打不掉的敌人（损失率达不到 90%），关卡不可胜。</li>
 *   <li><b>可布兵</b>：玩家名册里每个移动域，部署区都要有可放置的格子。</li>
 *   <li><b>装配</b>：BattleSetup 能把全部敌方预置实例化进世界。</li>
 * </ul>
 *
 * <p>未定位到 {@code data/} 目录时整体跳过（assume）。</p>
 */
@DisplayName("集成：关卡设计自洽性（落地/可胜/可布兵）")
class LevelIntegrityIT {

    private Path root;
    private UnitDefLoader units;

    @BeforeEach
    void setUp() throws IOException {
        root = com.gduws.testkit.Fixtures.dataRoot();
        assumeTrue(root != null, "未定位到 data/ 目录，跳过关卡自洽性集成测试");
        units = new UnitDefLoader();
        units.loadDirectory(root.resolve("units"));
    }

    /** 扫描全部关卡文件（与 GameFrame 装载逻辑一致：data/levels/*.json）。 */
    private List<Path> levelFiles() throws IOException {
        try (Stream<Path> s = Files.list(root.resolve("levels"))) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().collect(Collectors.toList());
        }
    }

    @Test
    @DisplayName("至少存在 5 个关卡（level_01 + 4 个主题关卡）")
    void hasExpectedLevels() throws IOException {
        List<Path> files = levelFiles();
        assertTrue(files.size() >= 5, "应至少有 5 个关卡，实际 " + files.size());
    }

    @Test
    @DisplayName("每个敌方预置单位都落在其移动域可通行的地形上")
    void allEnemiesOnTraversableTerrain() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Path f : levelFiles()) {
            LevelDef level = new LevelLoader().loadFile(f);
            GameMap map = new MapLoader().loadFile(root.resolve(stripData(level.mapPath)));
            for (LevelDef.PlacedUnit p : level.enemyUnits) {
                UnitDef def = units.get(p.unitId);
                if (def == null) {
                    bad.add(level.id + ": 未知兵种 " + p.unitId);
                    continue;
                }
                if (!map.isPassable(p.col, p.row, def.movementType)) {
                    bad.add(level.id + ": " + p.unitId + " 在 (" + p.col + "," + p.row
                            + ") 对 " + def.movementType + " 不可通行");
                }
            }
        }
        assertTrue(bad.isEmpty(), "敌方单位落点非法：\n" + String.join("\n", bad));
    }

    @Test
    @DisplayName("可胜性：玩家名册的攻击域覆盖敌方出现的每一层")
    void playerRosterCanHitEveryEnemyLayer() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Path f : levelFiles()) {
            LevelDef level = new LevelLoader().loadFile(f);
            assertFalse(level.playerBudget.isEmpty(), level.id + " 玩家名册为空");
            // 用名册兵种各造一个样本单位，作为"攻击者集合"
            List<UnitDef> roster = new ArrayList<>();
            for (String id : level.playerBudget.keySet()) {
                UnitDef d = units.get(id);
                assertTrue(d != null, level.id + " 名册含未知兵种 " + id);
                roster.add(d);
            }
            for (LevelDef.PlacedUnit p : level.enemyUnits) {
                UnitDef edef = units.get(p.unitId);
                if (edef == null) {
                    bad.add(level.id + ": 敌方含未知兵种 " + p.unitId);
                    continue;
                }
                Unit enemy = new Unit(edef, Faction.ENEMY, 0, 0);
                boolean killable = roster.stream()
                        .anyMatch(rd -> rd.attack != null && rd.attack.canTarget(enemy));
                if (!killable) {
                    bad.add(level.id + ": 无任何玩家兵种可命中 " + p.unitId
                            + "（层 " + enemy.layer() + "）");
                }
            }
        }
        assertTrue(bad.isEmpty(), "存在不可击杀的敌方（关卡不可胜）：\n" + String.join("\n", bad));
    }

    @Test
    @DisplayName("可布兵：玩家名册的每个移动域，部署区都有可放置格")
    void everyRosterDomainHasDeployableCells() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Path f : levelFiles()) {
            LevelDef level = new LevelLoader().loadFile(f);
            GameMap map = new MapLoader().loadFile(root.resolve(stripData(level.mapPath)));
            Set<MovementType> domains = new LinkedHashSet<>();
            for (String id : level.playerBudget.keySet()) {
                domains.add(units.get(id).movementType);
            }
            for (MovementType mt : domains) {
                if (countDeployable(map, mt) <= 0) {
                    bad.add(level.id + ": 移动域 " + mt + " 在部署区无可放置格");
                }
            }
        }
        assertTrue(bad.isEmpty(), "部署区不足：\n" + String.join("\n", bad));
    }

    private static int countDeployable(GameMap map, MovementType mt) {
        int n = 0;
        for (int r = 0; r < map.rows; r++) {
            for (int c = 0; c < map.cols; c++) {
                if (map.isDeployable(c, r, mt)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** 关卡里的 map 路径形如 "data/maps/xxx.map"，相对 dataRoot 需去掉前缀 "data/"。 */
    private static String stripData(String mapPath) {
        return mapPath.startsWith("data/") ? mapPath.substring("data/".length()) : mapPath;
    }
}
