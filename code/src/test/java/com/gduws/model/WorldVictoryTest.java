package com.gduws.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.testkit.Fixtures;

/**
 * {@link World} 胜负判定单元测试（FR-04）。
 *
 * <p>用左右隔墙、双方永不相遇的世界隔离胜负逻辑：直接置 hp=0 模拟战损，再推进 tick 触发判定。
 * 关联：BR-04-1（90% 损失判负）、BR-04-2（僵持 900 tick 超时）、BR-04-3（损失率高者负，相等玩家胜）。</p>
 *
 * <p><b>已发现的规格/实现偏差</b>见 {@link #loss90_atExactly10pct_currentBehavior_characterization}
 * 与 {@link #loss90_atExactly10pct_perSpec_FR04}。</p>
 */
@DisplayName("World 胜负判定（FR-04）")
class WorldVictoryTest {

    /** 将某阵营前 n 个单位置为阵亡（hp=0），下一 tick 会被 removeDead 清理。 */
    private static void killFirst(World w, Faction f, int n) {
        int killed = 0;
        for (Unit u : w.units) {
            if (u.faction == f && killed < n) {
                u.hp = 0;
                killed++;
            }
        }
    }

    /** 推进 world 直到产生胜者或达到上限，返回结束时的 tick 数。 */
    private static int runUntilWinner(World w, int maxTicks) {
        for (int t = 0; t < maxTicks; t++) {
            w.tick();
            if (w.winner() != null) {
                return w.tickCount();
            }
        }
        return -1;
    }

    @Test
    @DisplayName("BR-04-1：存活率 5% (1/20) < 10% → 玩家判负，敌方胜")
    void loss90_survival5pct_playerLoses() {
        World w = Fixtures.separatedWorld(20, 15);
        w.startBattle();

        killFirst(w, Faction.PLAYER, 19); // 仅剩 1/20 = 5%
        w.tick();

        assertEquals(Faction.ENEMY, w.winner());
    }

    @Test
    @DisplayName("BR-04-1：存活率恰好 10% (2/20) → 不应触发 90% 损失判定")
    void loss90_atExactly10pct_noDefeat() {
        World w = Fixtures.separatedWorld(20, 15);
        w.startBattle();

        killFirst(w, Faction.PLAYER, 18); // 剩 2/20 = 10%
        w.tick();

        assertNull(w.winner(), "存活率恰好 10% 不满足 '< 10%'，不应判负");
    }

    @Test
    @DisplayName("[规格期望 FR-04] 存活率恰好 10% → 不应触发 90% 损失判定")
    void loss90_atExactly10pct_perSpec_FR04() {
        World w = Fixtures.separatedWorld(20, 15);
        w.startBattle();

        killFirst(w, Faction.PLAYER, 18); // 剩 2/20 = 10%
        w.tick();

        assertNull(w.winner(), "FR-04：存活率 = 10% 不满足 '< 10%'，不应判负");
    }

    @Test
    @DisplayName("BR-04-2 + BR-04-3：僵持 2500 tick 且双方损失率相等 → 玩家获胜")
    void stalemate_equalLoss_playerWins() {
        World w = Fixtures.separatedWorld(5, 5);
        w.startBattle(); // 双方均无战损，损失率相等（均为 0）

        runUntilWinner(w, 3000);

        assertNotNull(w.winner(), "应在僵持超时后判定");
        assertEquals(Faction.PLAYER, w.winner(), "BR-04-3：损失率相等时玩家获胜");
    }

    @Test
    @DisplayName("BR-04-2 + BR-04-3：僵持超时且敌方损失率更高 → 敌方判负，玩家胜")
    void stalemate_higherLossFactionLoses() {
        World w = Fixtures.separatedWorld(10, 10);
        w.startBattle();

        // 制造一次战损后再无伤亡：玩家损失 30%(3/10)，敌方损失 50%(5/10)
        killFirst(w, Faction.PLAYER, 3);
        killFirst(w, Faction.ENEMY, 5);
        w.tick(); // 清理阵亡、刷新最近损失时间戳

        runUntilWinner(w, 3000);

        assertNotNull(w.winner());
        assertEquals(Faction.PLAYER, w.winner(), "敌方损失率更高 → 敌方判负");
    }

    @Test
    @DisplayName("失败方剩余单位停止行动（清空路径与移动目标）")
    void loserUnitsStopMoving() {
        World w = Fixtures.separatedWorld(20, 15);
        w.startBattle();
        killFirst(w, Faction.PLAYER, 19);
        w.tick();

        assertEquals(Faction.ENEMY, w.winner());
        for (Unit u : w.units) {
            assertNull(u.path, "判定结束后所有单位路径应被清空");
        }
    }

    @Test
    @DisplayName("BR-04-1：存活率 11% (3/27) > 10% → 不应触发 90% 损失判定")
    void loss90_survival11pct_noDefeat() {
        World w = Fixtures.separatedWorld(27, 15);
        w.startBattle();

        killFirst(w, Faction.PLAYER, 24); // 剩 3/27 ≈ 11.1%
        w.tick();

        assertNull(w.winner(), "存活率 > 10% 不应触发判定");
    }

    @Test
    @DisplayName("BR-04-1：敌方存活率 5% → 敌方判负，玩家胜")
    void loss90_enemySurvival5pct_enemyLoses() {
        World w = Fixtures.separatedWorld(15, 20);
        w.startBattle();

        killFirst(w, Faction.ENEMY, 19); // 敌方仅剩 1/20 = 5%
        w.tick();

        assertEquals(Faction.PLAYER, w.winner());
    }

    @Test
    @DisplayName("BR-04-2：僵持未达超时阈值 → 不应判定")
    void stalemate_beforeTimeout_noVictory() {
        World w = Fixtures.separatedWorld(5, 5);
        w.startBattle();

        int ticksBeforeTimeout = 2499; // STALEMATE_TIMEOUT = 2500
        for (int i = 0; i < ticksBeforeTimeout; i++) {
            w.tick();
        }

        assertNull(w.winner(), "僵持未超时不应判定");
    }

    @Test
    @DisplayName("BR-04-2：僵持刚好达到超时阈值 → 应触发判定")
    void stalemate_atTimeout_triggersVictory() {
        World w = Fixtures.separatedWorld(5, 5);
        w.startBattle();

        runUntilWinner(w, 3000);

        assertNotNull(w.winner(), "僵持达到超时阈值应触发判定");
    }

    @Test
    @DisplayName("BR-04-2 + BR-04-3：僵持超时且玩家损失率更高 → 玩家判负")
    void stalemate_playerHigherLoss_playerLoses() {
        World w = Fixtures.separatedWorld(10, 10);
        w.startBattle();

        killFirst(w, Faction.PLAYER, 6); // 玩家损失 60%
        killFirst(w, Faction.ENEMY, 3);  // 敌方损失 30%
        w.tick();

        runUntilWinner(w, 3000);

        assertNotNull(w.winner());
        assertEquals(Faction.ENEMY, w.winner(), "玩家损失率更高 → 玩家判负");
    }

    @Test
    @DisplayName("胜负判定后 tick 不再改变 winner")
    void afterVictory_winnerIsImmutable() {
        World w = Fixtures.separatedWorld(20, 15);
        w.startBattle();

        killFirst(w, Faction.PLAYER, 19);
        w.tick();
        assertEquals(Faction.ENEMY, w.winner());

        Faction firstWinner = w.winner();
        w.tick();
        w.tick();

        assertEquals(firstWinner, w.winner(), "判定后 winner 不应改变");
    }

    @Test
    @DisplayName("战斗未开始时不应触发胜负判定")
    void beforeBattleStart_noVictory() {
        World w = Fixtures.separatedWorld(20, 15);
        // 不调用 startBattle()

        killFirst(w, Faction.PLAYER, 19);
        w.tick();

        assertNull(w.winner(), "未调用 startBattle 时不应判定");
    }

    @Test
    @DisplayName("双方初始数量不同时损失率计算正确")
    void lossRatio_withAsymmetricForces() {
        World w = Fixtures.separatedWorld(30, 10);
        w.startBattle();

        // 双方各损失 50%
        killFirst(w, Faction.PLAYER, 15); // 30 → 15
        killFirst(w, Faction.ENEMY, 5);   // 10 → 5
        w.tick();

        runUntilWinner(w, 3000);

        assertEquals(Faction.PLAYER, w.winner(), "损失率相等时玩家应获胜");
    }

    @Test
    @DisplayName("兵力损失后重置僵持计时器")
    void lossResetsStalemate() {
        World w = Fixtures.separatedWorld(10, 10);
        w.startBattle();

        // 推进接近超时
        for (int i = 0; i < 2400; i++) {
            w.tick();
        }

        // 产生损失
        killFirst(w, Faction.PLAYER, 1);
        w.tick();

        // 再推进未达超时阈值
        for (int i = 0; i < 2400; i++) {
            w.tick();
        }

        assertNull(w.winner(), "损失后重置计时器，未达新超时阈值不应判定");
    }

    @Test
    @DisplayName("单位目标为阵亡单位时正确清理")
    void targetCleanup_whenUnitDies() {
        World w = Fixtures.separatedWorld(5, 5);
        w.startBattle();

        Unit target = w.units.get(0);
        Unit attacker = w.units.get(w.units.size() - 1);
        attacker.currentTarget = target;

        target.hp = 0;
        w.tick();

        assertNull(attacker.currentTarget, "目标单位阵亡后 currentTarget 应被清空");
    }
}
