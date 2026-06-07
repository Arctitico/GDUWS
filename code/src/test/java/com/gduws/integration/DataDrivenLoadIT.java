package com.gduws.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.control.BattleSetup;
import com.gduws.data.LevelLoader;
import com.gduws.data.MapLoader;
import com.gduws.data.UnitDefLoader;
import com.gduws.model.Faction;
import com.gduws.model.GameMap;
import com.gduws.model.LevelDef;
import com.gduws.model.MovementType;
import com.gduws.model.Unit;
import com.gduws.model.UnitDef;
import com.gduws.model.UnitRole;
import com.gduws.model.World;
import com.gduws.testkit.Fixtures;

/**
 * 集成测试：用仓库真实数据文件驱动加载与装配（NFR-09 数据驱动、FR-01 关卡加载、§5.1.1 单位属性）。
 *
 * <p>若运行环境未定位到 {@code data/} 目录则整体跳过（assume）。</p>
 */
@DisplayName("集成：真实数据驱动加载")
class DataDrivenLoadIT {

    /** §5.1.1 单位属性基线（与规格表逐项核对）。 */
    private record Spec(String id, MovementType mt, int hp, double radius, double speed,
                        int sight, int range, int dmg, int cooldown) { }

    private static final Spec[] UNIT_SPECS = {
        new Spec("light_tank",  MovementType.LAND,       210, 11, 1.1,  120, 130, 25, 75),
        new Spec("heavy_tank",  MovementType.LAND,       420, 14, 0.85, 120, 150, 35, 80),
        new Spec("interceptor", MovementType.AIR,        160, 10, 4.0,  130, 110, 20, 35),
        new Spec("bomber",      MovementType.AIR,        200, 12, 3.2,  80,  40,  40, 90),
        new Spec("battleship",  MovementType.WATER,      520, 18, 0.7,  140, 180, 45, 110),
        new Spec("destroyer",   MovementType.WATER,      320, 15, 0.9,  130, 140, 18, 40),
        new Spec("submarine",   MovementType.UNDERWATER, 240, 13, 0.8,  100, 120, 50, 100),
    };

    private Path root;
    private UnitDefLoader units;

    @BeforeEach
    void setUp() throws IOException {
        root = Fixtures.dataRoot();
        assumeTrue(root != null, "未定位到 data/ 目录，跳过数据驱动集成测试");
        units = new UnitDefLoader();
        units.loadDirectory(root.resolve("units"));
    }

    @Test
    @DisplayName("NFR-09 + §5.1.1：加载全部 7 种单位，属性与规格表一致")
    void loadsAllSevenUnitsWithSpecAttributes() {
        assertTrue(units.all().size() >= 7, "应至少加载 7 种单位");
        for (Spec s : UNIT_SPECS) {
            UnitDef d = units.get(s.id());
            assertEquals(s.mt(), d.movementType, s.id() + " 移动域");
            assertEquals(s.hp(), d.maxHp, s.id() + " HP");
            assertEquals(s.radius(), d.radius, 1e-9, s.id() + " 半径");
            assertEquals(s.speed(), d.moveSpeed, 1e-9, s.id() + " 速度");
            assertEquals(s.sight(), d.sightRange, s.id() + " 视野");
            assertEquals(s.range(), d.attack.maxAttackRange, s.id() + " 射程");
            assertEquals(s.dmg(), d.attack.directDamage, s.id() + " 伤害");
            assertEquals(s.cooldown(), d.attack.shootDelay, s.id() + " 冷却");
        }
    }

    @Test
    @DisplayName("[特征化] 攻击域数据与 §5.1.2 矩阵的一处差异：战列舰/驱逐舰数据可打陆地")
    void attackDomainsCharacterizeDataVsSpec() {
        // 与规格 §5.1.2 一致的样本
        assertTrue(units.get("interceptor").attack.canAttackAir);
        assertFalse(units.get("interceptor").attack.canAttackLand);
        assertTrue(units.get("submarine").attack.canAttackUnderwater);

        // 偏差记录：规格 §5.1.2 矩阵中战列舰、驱逐舰"陆地"列为 ✗，
        // 但数据文件 battleship.json / destroyer.json 的 canAttackLand 为 true。
        assertTrue(units.get("battleship").attack.canAttackLand,
                "数据现状：战列舰可打陆（规格 §5.1.2 为不可）");
        assertTrue(units.get("destroyer").attack.canAttackLand,
                "数据现状：驱逐舰可打陆（规格 §5.1.2 为不可）");
    }

    @Test
    @DisplayName("FR-01：加载关卡 level_01 的预算与敌方预置")
    void loadsLevel01() throws IOException {
        LevelDef level = new LevelLoader().loadFile(root.resolve("levels/level_01.json"));
        assertEquals("level_01", level.id);
        assertEquals(10, level.playerBudget.get("light_tank"));
        assertFalse(level.enemyUnits.isEmpty());
        LevelDef.PlacedUnit first = level.enemyUnits.get(0);
        assertEquals(UnitRole.SCOUT, first.role);
        assertEquals(45, first.col);
        assertEquals(5, first.row);
    }

    @Test
    @DisplayName("加载地图 level_01.map（50×50，格 20px）")
    void loadsMap01() throws IOException {
        GameMap m = new MapLoader().loadFile(root.resolve("maps/level_01.map"));
        assertEquals(50, m.cols);
        assertEquals(50, m.rows);
        assertEquals(20, m.tileSize);
    }

    @Test
    @DisplayName("端到端装配：BattleSetup 把关卡敌方预置实例化进世界")
    void assemblesEnemiesIntoWorld() throws IOException {
        LevelDef level = new LevelLoader().loadFile(root.resolve("levels/level_01.json"));
        GameMap map = new MapLoader().loadFile(root.resolve("maps/level_01.map"));
        World world = new World(map);

        new BattleSetup(units).placeEnemies(world, level);

        assertEquals(level.enemyUnits.size(), world.units.size(), "应装配全部敌方预置单位");
        for (Unit u : world.units) {
            assertEquals(Faction.ENEMY, u.faction);
        }
    }
}
