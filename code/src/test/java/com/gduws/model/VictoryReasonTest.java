package com.gduws.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.testkit.Fixtures;

/**
 * {@link World#victoryReason()} 结算原因判定单元测试（FR-04）。
 *
 * <p>沿用 {@link WorldVictoryTest} 的隔离世界：左右隔墙、双方永不相遇，直接置 hp=0 制造战损，
 * 再推进 tick 触发判定，仅核对结算「原因」字段。关联：BR-04-1（全歼/损失超 90%）、BR-04-2（僵持超时）。</p>
 */
@DisplayName("World 结算原因（FR-04）")
class VictoryReasonTest {

    private static void killFirst(World w, Faction f, int n) {
        int killed = 0;
        for (Unit u : w.units) {
            if (u.faction == f && killed < n) {
                u.hp = 0;
                killed++;
            }
        }
    }

    private static void runUntilWinner(World w, int maxTicks) {
        for (int t = 0; t < maxTicks && w.winner() == null; t++) {
            w.tick();
        }
    }

    @Test
    @DisplayName("未分胜负前 victoryReason 为 null")
    void noReasonBeforeDecision() {
        World w = Fixtures.separatedWorld(20, 15);
        w.startBattle();
        w.tick();
        assertNull(w.victoryReason());
    }

    @Test
    @DisplayName("失败方被全部歼灭 → ANNIHILATION")
    void annihilation() {
        World w = Fixtures.separatedWorld(20, 15);
        w.startBattle();

        killFirst(w, Faction.PLAYER, 20); // 玩家全灭
        w.tick();

        assertEquals(Faction.ENEMY, w.winner());
        assertEquals(VictoryReason.ANNIHILATION, w.victoryReason());
    }

    @Test
    @DisplayName("失败方损失超 90% 但仍有残存 → ATTRITION")
    void attrition() {
        World w = Fixtures.separatedWorld(20, 15);
        w.startBattle();

        killFirst(w, Faction.PLAYER, 19); // 剩 1/20 = 5%，未全灭
        w.tick();

        assertEquals(Faction.ENEMY, w.winner());
        assertEquals(VictoryReason.ATTRITION, w.victoryReason());
    }

    @Test
    @DisplayName("僵持超时按战损率判定 → STALEMATE")
    void stalemate() {
        World w = Fixtures.separatedWorld(5, 5);
        w.startBattle(); // 双方均无战损

        runUntilWinner(w, 3000);

        assertEquals(VictoryReason.STALEMATE, w.victoryReason());
    }
}
