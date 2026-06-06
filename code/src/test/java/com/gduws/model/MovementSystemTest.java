package com.gduws.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.testkit.Fixtures;

/**
 * {@link MovementSystem} 沿路径推进与朝向插值单元测试。
 *
 * <p>关联：BR-03-5（最大转向速率 π/16 每 tick）、FR-03 移动执行（到达格中心后出队）。</p>
 */
@DisplayName("MovementSystem 移动与转向")
class MovementSystemTest {

    private final MovementSystem system = new MovementSystem();
    private final GameMap map = Fixtures.landMap(10, 10); // tileSize 20

    private World worldWith(Unit u) {
        World w = new World(map);
        w.addUnit(u);
        return w;
    }

    private static Deque<Point> pathTo(int col, int row) {
        Deque<Point> dq = new ArrayDeque<>();
        dq.add(new Point(col, row));
        return dq;
    }

    @Test
    @DisplayName("沿路径朝下一格中心前进一个 moveSpeed")
    void movesTowardNextCell() {
        Unit u = Fixtures.unitAtCell(map, Fixtures.landTank(), Faction.PLAYER, 0, 0); // (10,10), speed 1.1
        u.path = pathTo(1, 0); // 目标格中心 (30,10)
        World w = worldWith(u);

        system.update(w);

        assertEquals(11.1, u.x, 1e-9, "向东推进一个 speed");
        assertEquals(10.0, u.y, 1e-9);
        assertEquals(1, u.path.size(), "尚未到达，路径不出队");
    }

    @Test
    @DisplayName("距离 ≤ 一步时吸附到格中心并出队，路径走完置空 moveGoal")
    void snapsAndDequeuesOnArrival() {
        Unit u = Fixtures.unitAtCell(map, Fixtures.landTank(), Faction.PLAYER, 0, 0);
        u.x = 29; // 距目标格中心 (30,10) 仅 1px ≤ speed 1.1
        u.path = pathTo(1, 0);
        u.moveGoal = new Point(1, 0);
        World w = worldWith(u);

        system.update(w);

        assertEquals(30.0, u.x, 1e-9);
        assertEquals(10.0, u.y, 1e-9);
        assertTrue(u.path.isEmpty(), "到达后出队");
        assertEquals(null, u.moveGoal, "路径走完清空 moveGoal");
    }

    @Test
    @DisplayName("BR-03-5：单 tick 转向不超过 π/16")
    void turnRateCappedAtPiOver16() {
        Unit u = Fixtures.unitAtCell(map, Fixtures.landTank(), Faction.PLAYER, 0, 0);
        u.facing = Math.PI;      // 朝西
        u.path = pathTo(1, 0);   // 目标在正东（角度 0），需转向 180°
        World w = worldWith(u);

        system.update(w);

        // 单 tick 至多朝目标转 π/16：π → π - π/16
        assertEquals(Math.PI - Math.PI / 16, u.facing, 1e-9);
    }

    @Test
    @DisplayName("路径为空或 null 时不移动")
    void noMovementWithoutPath() {
        Unit u = Fixtures.unitAtCell(map, Fixtures.landTank(), Faction.PLAYER, 3, 3);
        double x0 = u.x;
        double y0 = u.y;
        u.path = null;
        World w = worldWith(u);

        system.update(w);

        assertEquals(x0, u.x, 1e-9);
        assertEquals(y0, u.y, 1e-9);
    }
}
