package com.gduws.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.gduws.model.Faction;
import com.gduws.model.GameMap;
import com.gduws.model.LevelDef;
import com.gduws.model.MovementType;
import com.gduws.model.TerrainType;
import com.gduws.model.Unit;
import com.gduws.model.UnitRole;
import com.gduws.model.World;
import com.gduws.testkit.Fixtures;

/**
 * {@link DeployController} 布兵控制器单元测试（FR-02）。
 *
 * <p>关联：BR-02-1（兵力预算上限）、BR-02-2（禁布区）、BR-02-3（移动域↔地形）、BR-02-4（默认打击角色）、
 * 移除返还预算、角色切换。单位定义经 {@link UnitDefLoader} 从临时 JSON 文件加载，顺带覆盖加载链路。</p>
 */
@DisplayName("DeployController 布兵控制（FR-02）")
class DeployControllerTest {

    private static final String LIGHT_TANK_JSON = """
        {
          "id": "light_tank",
          "displayName": "轻型坦克",
          "maxHp": 210,
          "radius": 11,
          "movementType": "LAND",
          "moveSpeed": 1.1,
          "sightRange": 120,
          "attack": { "canAttackLand": true, "canAttackWaterSurface": true,
                      "maxAttackRange": 130, "directDamage": 25, "shootDelay": 75 }
        }
        """;

    private UnitDefLoader loader;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("light_tank.json"), LIGHT_TANK_JSON, StandardCharsets.UTF_8);
        loader = new UnitDefLoader();
        loader.loadDirectory(tmp);
    }

    private DeployController controllerFor(World w, int budget) {
        LevelDef level = new LevelDef();
        level.playerBudget.put("light_tank", budget);
        return new DeployController(w, loader, level);
    }

    private World landWorld() {
        return new World(Fixtures.landMap(30, 30));
    }

    @Test
    @DisplayName("BR-02-1：预算耗尽后放置无效（预算 3，点 4 次 → 前 3 成功）")
    void budgetExhaustion() {
        World w = landWorld();
        DeployController c = controllerFor(w, 3);
        GameMap m = w.map;

        assertTrue(c.tryPlace(m.cellCenterX(5), m.cellCenterY(5)));
        assertTrue(c.tryPlace(m.cellCenterX(10), m.cellCenterY(5)));
        assertTrue(c.tryPlace(m.cellCenterX(15), m.cellCenterY(5)));
        assertFalse(c.tryPlace(m.cellCenterX(20), m.cellCenterY(5)), "第 4 次应失败");

        assertEquals(0, c.remaining().get("light_tank"));
        assertTrue(c.lastMessage().contains("用尽"));
        assertEquals(3, w.units.size());
    }

    @Test
    @DisplayName("移除单位返还预算")
    void removeRefundsBudget() {
        World w = landWorld();
        DeployController c = controllerFor(w, 2);
        GameMap m = w.map;

        c.tryPlace(m.cellCenterX(5), m.cellCenterY(5));
        c.tryPlace(m.cellCenterX(10), m.cellCenterY(5));
        assertEquals(0, c.remaining().get("light_tank"));

        assertTrue(c.tryRemove(m.cellCenterX(5), m.cellCenterY(5)));
        assertEquals(1, c.remaining().get("light_tank"), "移除后预算 +1");
        assertEquals(1, w.units.size());
    }

    @Test
    @DisplayName("BR-02-4：新部署单位默认角色为打击")
    void defaultRoleIsStrike() {
        World w = landWorld();
        DeployController c = controllerFor(w, 1);
        GameMap m = w.map;

        c.tryPlace(m.cellCenterX(8), m.cellCenterY(8));
        Unit u = w.unitAt(m.cellCenterX(8), m.cellCenterY(8), 0);
        assertNotNull(u);
        assertEquals(UnitRole.STRIKE, u.role);
        assertEquals(Faction.PLAYER, u.faction);
    }

    @Test
    @DisplayName("BR-02-2：禁布区不可放置")
    void forbiddenZoneBlocksPlacement() {
        World w = landWorld();
        Fixtures.setDeployable(w.map, 8, 8, false);
        DeployController c = controllerFor(w, 3);

        assertFalse(c.tryPlace(w.map.cellCenterX(8), w.map.cellCenterY(8)));
        assertTrue(c.lastMessage().contains("禁止布兵"));
        assertEquals(0, w.units.size());
    }

    @Test
    @DisplayName("BR-02-3：单位不能放在其移动域不可通行的地形上")
    void terrainRestrictionBlocksPlacement() {
        World w = landWorld();
        Fixtures.setTerrain(w.map, 8, 8, TerrainType.WATER); // 陆地单位不可入水
        DeployController c = controllerFor(w, 3);

        assertFalse(c.tryPlace(w.map.cellCenterX(8), w.map.cellCenterY(8)));
        assertTrue(c.lastMessage().contains("地形"));
        assertEquals(0, w.units.size());
    }

    @Test
    @DisplayName("左键点击已部署单位切换侦察/打击角色")
    void toggleRole() {
        World w = landWorld();
        DeployController c = controllerFor(w, 1);
        GameMap m = w.map;
        c.tryPlace(m.cellCenterX(8), m.cellCenterY(8));

        assertTrue(c.toggleRoleAt(m.cellCenterX(8), m.cellCenterY(8)));
        assertEquals(UnitRole.SCOUT, w.unitAt(m.cellCenterX(8), m.cellCenterY(8), 0).role);

        assertTrue(c.toggleRoleAt(m.cellCenterX(8), m.cellCenterY(8)));
        assertEquals(UnitRole.STRIKE, w.unitAt(m.cellCenterX(8), m.cellCenterY(8), 0).role);
    }

    @Test
    @DisplayName("不能在已有单位处重叠放置")
    void overlapBlocksPlacement() {
        World w = landWorld();
        DeployController c = controllerFor(w, 3);
        GameMap m = w.map;

        assertTrue(c.tryPlace(m.cellCenterX(8), m.cellCenterY(8)));
        assertFalse(c.tryPlace(m.cellCenterX(8), m.cellCenterY(8)), "同格重叠应失败");
        assertTrue(c.lastMessage().contains("重叠"));
        assertEquals(1, w.units.size());
    }

    @Test
    @DisplayName("初始默认选中第一个预算单位类型")
    void defaultsToFirstBudgetedUnit() {
        DeployController c = controllerFor(landWorld(), 5);
        assertEquals("light_tank", c.selectedUnitId());
        assertEquals(MovementType.LAND, c.defOf("light_tank").movementType);
    }
}
