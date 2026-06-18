package com.gduws.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gduws.data.UnitDefLoader;
import com.gduws.model.GameMap;
import com.gduws.model.LevelDef;
import com.gduws.model.World;
import com.gduws.testkit.Fixtures;

/**
 * {@link DeployController} 资金制布兵单元测试（FR-02 扩展：兵种价格 + 关卡资金）。
 *
 * <p>覆盖：资金随放置扣减 / 随移除返还、资金不足拒绝放置、数量上限与资金双重约束、
 * 以及关卡未给资金时退化为纯计数制（不消耗资金）。两种兵种各带价格，由临时 JSON 加载。</p>
 */
@DisplayName("DeployController 资金制布兵（FR-02 扩展）")
class DeployFundsTest {

    private static final String TANK_JSON = """
        { "id":"light_tank", "displayName":"轻型坦克", "maxHp":210, "cost":100, "radius":11,
          "movementType":"LAND", "moveSpeed":1.1, "sightRange":120,
          "attack": { "canAttackLand":true, "maxAttackRange":130, "directDamage":25, "shootDelay":75 } }
        """;
    private static final String HEAVY_JSON = """
        { "id":"heavy_tank", "displayName":"重型坦克", "maxHp":420, "cost":250, "radius":11,
          "movementType":"LAND", "moveSpeed":0.85, "sightRange":120,
          "attack": { "canAttackLand":true, "maxAttackRange":150, "directDamage":35, "shootDelay":80 } }
        """;

    private UnitDefLoader loader;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("light_tank.json"), TANK_JSON, StandardCharsets.UTF_8);
        Files.writeString(tmp.resolve("heavy_tank.json"), HEAVY_JSON, StandardCharsets.UTF_8);
        loader = new UnitDefLoader();
        loader.loadDirectory(tmp);
    }

    private World landWorld() {
        return new World(Fixtures.landMap(30, 30));
    }

    /** 资金制关卡：总资金 funds，两种兵均高数量上限（让资金成为真正约束）。 */
    private DeployController fundsController(World w, int funds) {
        LevelDef level = new LevelDef();
        level.playerFunds = funds;
        level.playerBudget.put("light_tank", 99);
        level.playerBudget.put("heavy_tank", 99);
        return new DeployController(w, loader, level);
    }

    @Test
    @DisplayName("启用资金制：fundsMode 为真，初始剩余 = 总资金")
    void fundsModeEnabled() {
        DeployController c = fundsController(landWorld(), 300);
        assertTrue(c.fundsMode());
        assertEquals(300, c.totalFunds());
        assertEquals(300, c.remainingFunds());
        assertEquals(100, c.costOf("light_tank"));
    }

    @Test
    @DisplayName("放置扣减资金，移除返还资金")
    void spendAndRefund() {
        World w = landWorld();
        DeployController c = fundsController(w, 300);
        GameMap m = w.map;

        assertTrue(c.tryPlace(m.cellCenterX(5), m.cellCenterY(5)));   // -100
        assertEquals(200, c.remainingFunds());
        assertTrue(c.tryPlace(m.cellCenterX(10), m.cellCenterY(5)));  // -100
        assertEquals(100, c.remainingFunds());

        assertTrue(c.tryRemove(m.cellCenterX(5), m.cellCenterY(5)));  // +100
        assertEquals(200, c.remainingFunds());
    }

    @Test
    @DisplayName("资金不足时拒绝放置")
    void rejectWhenInsufficientFunds() {
        World w = landWorld();
        DeployController c = fundsController(w, 120); // 只够 1 个轻坦
        GameMap m = w.map;

        c.selectUnit("light_tank");
        assertTrue(c.tryPlace(m.cellCenterX(5), m.cellCenterY(5)));   // -100, 剩 20
        assertFalse(c.tryPlace(m.cellCenterX(10), m.cellCenterY(5)), "剩 20 < 100，应失败");
        assertTrue(c.lastMessage().contains("资金不足"));
        assertEquals(20, c.remainingFunds());
        assertEquals(1, w.units.size());
    }

    @Test
    @DisplayName("更贵的兵种买不起：资金足够轻坦但不够重坦")
    void mixedAffordability() {
        World w = landWorld();
        DeployController c = fundsController(w, 200);
        GameMap m = w.map;

        c.selectUnit("heavy_tank"); // 250 > 200
        assertFalse(c.tryPlace(m.cellCenterX(5), m.cellCenterY(5)));
        assertTrue(c.lastMessage().contains("资金不足"));

        c.selectUnit("light_tank"); // 100 ≤ 200
        assertTrue(c.tryPlace(m.cellCenterX(5), m.cellCenterY(5)));
        assertEquals(100, c.remainingFunds());
    }

    @Test
    @DisplayName("资金制不限数量：playerBudget 的数值仅作名册，不构成数量上限")
    void fundsModeIgnoresCountCap() {
        World w = landWorld();
        LevelDef level = new LevelDef();
        level.playerFunds = 1000;          // 资金可买 10 个轻坦
        level.playerBudget.put("light_tank", 1); // 数值仅表示"可用"，不限数量
        DeployController c = new DeployController(w, loader, level);
        GameMap m = w.map;

        // 连续放置 3 个：尽管 playerBudget 写的是 1，资金制下不受其约束
        assertTrue(c.tryPlace(m.cellCenterX(5), m.cellCenterY(5)));
        assertTrue(c.tryPlace(m.cellCenterX(10), m.cellCenterY(5)));
        assertTrue(c.tryPlace(m.cellCenterX(15), m.cellCenterY(5)));
        assertEquals(3, w.units.size());
        assertEquals(700, c.remainingFunds(), "仅资金被扣减");
    }

    @Test
    @DisplayName("未给资金 → 退化为纯计数制，不消耗资金")
    void legacyCountModeWhenNoFunds() {
        World w = landWorld();
        LevelDef level = new LevelDef(); // playerFunds 默认 0
        level.playerBudget.put("light_tank", 3);
        DeployController c = new DeployController(w, loader, level);
        GameMap m = w.map;

        assertFalse(c.fundsMode());
        assertTrue(c.tryPlace(m.cellCenterX(5), m.cellCenterY(5)));
        assertEquals(0, c.remainingFunds(), "计数制下剩余资金恒为 0");
        assertEquals(2, c.remaining().get("light_tank"));
    }
}
