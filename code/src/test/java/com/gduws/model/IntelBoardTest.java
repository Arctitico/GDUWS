package com.gduws.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.testkit.Fixtures;

/**
 * {@link IntelBoard} 阵营情报板单元测试。
 *
 * <p>关联：BR-03-1（视野内敌情写入情报板）、BR-03-2（阵营内情报共享）、情报记忆超时过期。</p>
 */
@DisplayName("IntelBoard 情报板")
class IntelBoardTest {

    private Unit enemy(double x, double y) {
        return Fixtures.unit(Fixtures.landTank(), Faction.ENEMY, x, y);
    }

    @Test
    @DisplayName("上报后可被查询，记录位置与时间戳")
    void reportThenKnown() {
        IntelBoard board = new IntelBoard();
        Unit e = enemy(100, 200);
        board.report(e, 100, 200, 5);

        assertTrue(board.hasAnyEnemy());
        assertEquals(1, board.knownEnemies().size());
        IntelBoard.IntelEntry entry = board.knownEnemies().iterator().next();
        assertEquals(e, entry.enemy);
        assertEquals(100, entry.x, 1e-9);
        assertEquals(200, entry.y, 1e-9);
        assertEquals(5, entry.lastSeenTick);
    }

    @Test
    @DisplayName("同一敌人再次上报只更新位置/时间，不重复计数")
    void reportUpdatesExistingEntry() {
        IntelBoard board = new IntelBoard();
        Unit e = enemy(100, 200);
        board.report(e, 100, 200, 5);
        board.report(e, 150, 250, 9);

        assertEquals(1, board.knownEnemies().size());
        IntelBoard.IntelEntry entry = board.knownEnemies().iterator().next();
        assertEquals(150, entry.x, 1e-9);
        assertEquals(9, entry.lastSeenTick);
    }

    @Test
    @DisplayName("forget 移除指定敌人")
    void forget() {
        IntelBoard board = new IntelBoard();
        Unit e = enemy(0, 0);
        board.report(e, 0, 0, 1);
        board.forget(e);
        assertFalse(board.hasAnyEnemy());
    }

    @Test
    @DisplayName("expireStale 边界：恰好等于超时仍保留，超出一拍才剔除")
    void expireStaleBoundary() {
        int timeout = 90;
        IntelBoard board = new IntelBoard();
        Unit e = enemy(0, 0);
        board.report(e, 0, 0, 0);

        board.expireStale(timeout, timeout);     // 90 - 0 = 90，不 > 90 → 保留
        assertTrue(board.hasAnyEnemy(), "恰好达到超时阈值时仍应记忆");

        board.expireStale(timeout + 1, timeout);  // 91 - 0 = 91 > 90 → 过期
        assertFalse(board.hasAnyEnemy(), "超过记忆时长应被剔除");
    }

    @Test
    @DisplayName("clearAll 清空所有敌情")
    void clearAll() {
        IntelBoard board = new IntelBoard();
        board.report(enemy(1, 1), 1, 1, 1);
        board.report(enemy(2, 2), 2, 2, 1);
        board.clearAll();
        assertFalse(board.hasAnyEnemy());
        assertEquals(0, board.knownEnemies().size());
    }
}
