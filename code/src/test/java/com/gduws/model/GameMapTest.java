package com.gduws.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.testkit.Fixtures;

/**
 * {@link GameMap} 通行性与坐标换算单元测试。
 *
 * <p>关联：§5.2 地形通行规则矩阵、BR-02-2 禁布区、BR-02-3 移动域 ↔ 地形约束、§5.5 坐标换算。</p>
 */
@DisplayName("GameMap 通行性与几何")
class GameMapTest {

    /** §5.2 通行矩阵：逐 (地形 × 移动域) 校验 isPassable。 */
    @Test
    @DisplayName("§5.2 地形 × 移动域 通行矩阵")
    void terrainPassabilityMatrix() {
        GameMap m = Fixtures.landMap(3, 3);
        // 在 3×3 地图上铺设四类地形样本格
        Fixtures.setTerrain(m, 0, 0, TerrainType.GRASS);     // LAND 地形
        Fixtures.setTerrain(m, 1, 0, TerrainType.WATER);     // WATER 地形
        Fixtures.setTerrain(m, 2, 0, TerrainType.MOUNTAIN);  // BLOCK 地形

        // LAND 单位：仅陆地地形可通行
        assertTrue(m.isPassable(0, 0, MovementType.LAND));
        assertFalse(m.isPassable(1, 0, MovementType.LAND));
        assertFalse(m.isPassable(2, 0, MovementType.LAND));

        // WATER / UNDERWATER 单位：仅水域地形可通行
        for (MovementType mt : new MovementType[]{MovementType.WATER, MovementType.UNDERWATER}) {
            assertFalse(m.isPassable(0, 0, mt), mt + " 不应能上陆地");
            assertTrue(m.isPassable(1, 0, mt), mt + " 应能进水域");
            assertFalse(m.isPassable(2, 0, mt), mt + " 不应能进山地");
        }

        // AIR 单位：无视地形，只要在界内
        assertTrue(m.isPassable(0, 0, MovementType.AIR));
        assertTrue(m.isPassable(1, 0, MovementType.AIR));
        assertTrue(m.isPassable(2, 0, MovementType.AIR));
    }

    @Test
    @DisplayName("浅水 / 深水均归类为水域可通行")
    void shallowAndDeepAreWater() {
        GameMap m = Fixtures.landMap(2, 1);
        Fixtures.setTerrain(m, 0, 0, TerrainType.SHALLOW);
        Fixtures.setTerrain(m, 1, 0, TerrainType.DEEP);
        assertTrue(m.isPassable(0, 0, MovementType.WATER));
        assertTrue(m.isPassable(1, 0, MovementType.UNDERWATER));
        assertFalse(m.isPassable(0, 0, MovementType.LAND));
    }

    @Test
    @DisplayName("越界格：非空中一律不可通行，空中越界也不可通行")
    void outOfBoundsNotPassable() {
        GameMap m = Fixtures.landMap(3, 3);
        assertFalse(m.isPassable(-1, 0, MovementType.LAND));
        assertFalse(m.isPassable(3, 0, MovementType.LAND));
        assertFalse(m.isPassable(0, 3, MovementType.AIR)); // AIR 也受 inBounds 约束
        assertFalse(m.inBounds(3, 3));
        assertTrue(m.inBounds(2, 2));
    }

    @Test
    @DisplayName("BR-02-2 / BR-02-3：可布兵 = 地形可通行 且 非禁布区")
    void deployability() {
        GameMap m = Fixtures.landMap(4, 4);
        Fixtures.setTerrain(m, 1, 1, TerrainType.WATER);   // 地形不允许陆地单位
        Fixtures.setDeployable(m, 2, 2, false);            // 禁布区

        assertTrue(m.isDeployable(0, 0, MovementType.LAND), "草地且未禁布 → 可布兵");
        assertFalse(m.isDeployable(1, 1, MovementType.LAND), "BR-02-3 地形不符 → 不可布兵");
        assertFalse(m.isDeployable(2, 2, MovementType.LAND), "BR-02-2 禁布区 → 不可布兵");

        assertTrue(m.isDeployForbidden(2, 2), "禁布区应被标记");
        assertFalse(m.isDeployForbidden(0, 0));
        assertFalse(m.isDeployForbidden(99, 99), "越界不算禁布区");
    }

    @Test
    @DisplayName("§5.5 像素 ↔ 格 坐标换算与格中心")
    void coordinateConversion() {
        GameMap m = Fixtures.landMap(10, 10); // tileSize = 20
        assertEquals(0, m.toCol(0));
        assertEquals(1, m.toCol(25));
        assertEquals(2, m.toRow(40));
        assertEquals(30.0, m.cellCenterX(1), 1e-9);   // 1*20 + 10
        assertEquals(50.0, m.cellCenterY(2), 1e-9);   // 2*20 + 10
        assertEquals(200, m.pixelWidth());
        assertEquals(200, m.pixelHeight());
    }

    @Test
    @DisplayName("findNearestPassable：从不可通行格向外扩展找最近可通行格")
    void findNearestPassable() {
        GameMap m = Fixtures.landMap(5, 5);
        Fixtures.setTerrain(m, 2, 2, TerrainType.MOUNTAIN); // 中心不可通行

        Point near = m.findNearestPassable(2, 2, MovementType.LAND, 2);
        assertNotNull(near);
        assertTrue(m.isPassable(near.x, near.y, MovementType.LAND));
        assertEquals(1, Math.max(Math.abs(near.x - 2), Math.abs(near.y - 2)),
                "最近可通行格应在 1 圈邻域内");
    }

    @Test
    @DisplayName("findNearestPassable：半径内无可通行格返回 null")
    void findNearestPassableReturnsNullWhenNone() {
        GameMap m = Fixtures.filledMap(3, 3, Fixtures.TILE, TerrainType.MOUNTAIN);
        assertNull(m.findNearestPassable(1, 1, MovementType.LAND, 1));
    }
}
