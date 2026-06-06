package com.gduws.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.testkit.Fixtures;

/**
 * {@link ExplorationMap} 侦察探索图单元测试。
 *
 * <p>关联：FR-03 侦察 AI"向最久未探索区域前进"。区块为 4×4 粗粒度，按阵营记录最近访问 tick。</p>
 */
@DisplayName("ExplorationMap 探索图")
class ExplorationMapTest {

    private static final int RS = ExplorationMap.REGION_SIZE;

    private static Point regionOf(Point cell) {
        return new Point(cell.x / RS, cell.y / RS);
    }

    @Test
    @DisplayName("区块行列数 = ceil(格数 / 区块边长)")
    void regionGridSize() {
        ExplorationMap e8 = new ExplorationMap(Fixtures.landMap(8, 8));
        assertEquals(2, e8.regionCols);
        assertEquals(2, e8.regionRows);

        ExplorationMap e10 = new ExplorationMap(Fixtures.landMap(10, 10));
        assertEquals(3, e10.regionCols); // ceil(10/4) = 3
        assertEquals(3, e10.regionRows);
    }

    @Test
    @DisplayName("全新探索图：选最远的未访问区块作为目标")
    void freshMapPicksFarthestRegion() {
        GameMap m = Fixtures.landMap(8, 8);
        ExplorationMap exp = new ExplorationMap(m);

        Point goal = exp.pickGoal(Faction.PLAYER, 0, 0, MovementType.LAND);
        assertNotNull(goal);
        assertTrue(m.isPassable(goal.x, goal.y, MovementType.LAND));
        assertEquals(new Point(1, 1), regionOf(goal), "应选离起点最远的区块 (1,1)");
    }

    @Test
    @DisplayName("避开最近访问过的区块，转向最久未访问的区块")
    void avoidsRecentlyVisitedRegions() {
        GameMap m = Fixtures.landMap(8, 8);
        ExplorationMap exp = new ExplorationMap(m);

        // 把区块 (0,1) 与 (1,1) 标记为"刚访问"，仅区块 (1,0) 仍为 tick 0
        exp.markVisited(Faction.PLAYER, m.cellCenterX(1), m.cellCenterY(5), 100); // 区块 (0,1)
        exp.markVisited(Faction.PLAYER, m.cellCenterX(5), m.cellCenterY(5), 100); // 区块 (1,1)

        Point goal = exp.pickGoal(Faction.PLAYER, 0, 0, MovementType.LAND);
        assertNotNull(goal);
        assertEquals(new Point(1, 0), regionOf(goal), "应转向唯一未访问的区块 (1,0)");
    }

    @Test
    @DisplayName("按阵营隔离：一方的访问记录不影响另一方")
    void perFactionIsolation() {
        GameMap m = Fixtures.landMap(8, 8);
        ExplorationMap exp = new ExplorationMap(m);

        // PLAYER 把除 (1,0) 外的区块都标记为最近访问
        exp.markVisited(Faction.PLAYER, m.cellCenterX(1), m.cellCenterY(5), 100); // (0,1)
        exp.markVisited(Faction.PLAYER, m.cellCenterX(5), m.cellCenterY(5), 100); // (1,1)

        // ENEMY 视角仍为全新 → 选最远区块 (1,1)
        Point enemyGoal = exp.pickGoal(Faction.ENEMY, 0, 0, MovementType.LAND);
        assertEquals(new Point(1, 1), regionOf(enemyGoal));
    }
}
