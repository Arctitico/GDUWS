package com.gduws.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.testkit.Fixtures;

/**
 * {@link Pathfinder} A* 寻路单元测试。
 *
 * <p>关联：BR-03-3（A*、8 邻接、按移动域过滤可通行地形）、BR-03-4（威胁场避敌寻路）。</p>
 */
@DisplayName("Pathfinder A* 寻路")
class PathfinderTest {

    private static List<Point> toList(Deque<Point> path) {
        return path == null ? null : new ArrayList<>(path);
    }

    @Test
    @DisplayName("开阔地图上的直线路径：步数 = 曼哈顿距离，终点正确")
    void straightLine() {
        GameMap m = Fixtures.landMap(10, 10);
        Pathfinder pf = new Pathfinder(m);
        List<Point> path = toList(pf.findPath(MovementType.LAND, 0, 0, 3, 0, false, null));
        assertNotNull(path);
        assertEquals(3, path.size());
        assertEquals(new Point(3, 0), path.get(path.size() - 1));
    }

    @Test
    @DisplayName("对角线路径：Octile 下 (0,0)→(3,3) 用 3 步对角走完")
    void diagonal() {
        GameMap m = Fixtures.landMap(10, 10);
        Pathfinder pf = new Pathfinder(m);
        List<Point> path = toList(pf.findPath(MovementType.LAND, 0, 0, 3, 3, false, null));
        assertNotNull(path);
        assertEquals(3, path.size());
        assertEquals(new Point(3, 3), path.get(path.size() - 1));
    }

    @Test
    @DisplayName("起点即终点：返回空路径（非 null）")
    void startEqualsGoal() {
        GameMap m = Fixtures.landMap(5, 5);
        Deque<Point> path = new Pathfinder(m).findPath(MovementType.LAND, 2, 2, 2, 2, false, null);
        assertNotNull(path);
        assertTrue(path.isEmpty());
    }

    @Test
    @DisplayName("终点不可通行 / 越界：返回 null")
    void unreachableGoal() {
        GameMap m = Fixtures.landMap(5, 5);
        Fixtures.setTerrain(m, 4, 4, TerrainType.MOUNTAIN);
        Pathfinder pf = new Pathfinder(m);
        assertNull(pf.findPath(MovementType.LAND, 0, 0, 4, 4, false, null), "山地终点不可达");
        assertNull(pf.findPath(MovementType.LAND, 0, 0, 9, 9, false, null), "越界终点不可达");
    }

    @Test
    @DisplayName("整列墙完全隔断：无路返回 null")
    void blockedByWall() {
        GameMap m = Fixtures.landMap(5, 5);
        for (int r = 0; r < 5; r++) {
            Fixtures.setTerrain(m, 2, r, TerrainType.MOUNTAIN); // 第 2 列整列设为山地
        }
        Deque<Point> path = new Pathfinder(m).findPath(MovementType.LAND, 0, 2, 4, 2, false, null);
        assertNull(path);
    }

    @Test
    @DisplayName("BR-03-3：路径按移动域过滤地形，绕开不可通行格")
    void terrainFiltering() {
        GameMap m = Fixtures.landMap(5, 5);
        Fixtures.setTerrain(m, 2, 2, TerrainType.WATER); // 陆地单位不可进入的水格挡在中间
        List<Point> path = toList(new Pathfinder(m).findPath(MovementType.LAND, 0, 2, 4, 2, false, null));
        assertNotNull(path);
        for (Point p : path) {
            assertTrue(m.isPassable(p.x, p.y, MovementType.LAND), "路径格 " + p + " 必须 LAND 可通行");
        }
        assertFalse(path.contains(new Point(2, 2)), "路径不应穿过水格");
    }

    @Test
    @DisplayName("水域单位无法把陆地作为终点")
    void waterUnitCannotGoOnLand() {
        GameMap m = Fixtures.waterMap(5, 5);
        Fixtures.setTerrain(m, 4, 2, TerrainType.GRASS); // 陆地终点
        assertNull(new Pathfinder(m).findPath(MovementType.WATER, 0, 2, 4, 2, false, null));
    }

    @Test
    @DisplayName("BR-03-4：威胁场避敌——避战路径绕开已知敌人所在格")
    void threatFieldAvoidance() {
        GameMap m = Fixtures.landMap(11, 11);
        Pathfinder pf = new Pathfinder(m);

        // 在直线必经格 (5,5) 处放一个已知敌人
        IntelBoard intel = new IntelBoard();
        Unit enemy = Fixtures.unitAtCell(m, Fixtures.landTank(), Faction.ENEMY, 5, 5);
        intel.report(enemy, enemy.x, enemy.y, 0);

        List<Point> direct = toList(pf.findPath(MovementType.LAND, 0, 5, 10, 5, false, null));
        List<Point> avoid  = toList(pf.findPath(MovementType.LAND, 0, 5, 10, 5, true, intel));

        assertNotNull(direct);
        assertNotNull(avoid);
        assertTrue(direct.contains(new Point(5, 5)), "直线路径应穿过敌人所在格");
        assertFalse(avoid.contains(new Point(5, 5)), "避战路径应绕开敌人所在格");
        assertEquals(new Point(10, 5), avoid.get(avoid.size() - 1), "避战路径仍须抵达终点");
    }
}
