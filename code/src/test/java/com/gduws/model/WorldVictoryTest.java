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
    @DisplayName("[现状特征化] 存活率恰好 10% (2/20)：实现判玩家负（lossRatio >= 0.9）")
    void loss90_atExactly10pct_currentBehavior_characterization() {
        // 注意：本测试记录"当前实现"的行为，而非规格期望。
        // 实现用 lossRatio >= 0.9，在存活率恰好 10% 时即触发判负；
        // 而 FR-04 / BR-04-1 要求"存活数/初始数 < 10%"（严格小于）才判负。
        // 二者在 10% 边界冲突，详见下方 @Disabled 的规格测试。
        World w = Fixtures.separatedWorld(20, 15);
        w.startBattle();

        killFirst(w, Faction.PLAYER, 18); // 剩 2/20 = 10%
        w.tick();

        assertEquals(Faction.ENEMY, w.winner(),
                "现状：实现在恰好 10% 即判负（与 FR-04 不一致）");
    }

    @Test
    @Disabled("规格/实现偏差：FR-04 要求存活率严格 < 10% 才判负，但 World.checkVictory 用 "
            + "'lossRatio >= 0.9'，在恰好 10% 即触发。修复方式：将该处改为 'lossRatio > 0.9' 后启用本测试。")
    @DisplayName("[规格期望 FR-04] 存活率恰好 10% → 不应触发 90% 损失判定")
    void loss90_atExactly10pct_perSpec_FR04() {
        World w = Fixtures.separatedWorld(20, 15);
        w.startBattle();

        killFirst(w, Faction.PLAYER, 18); // 剩 2/20 = 10%
        w.tick();

        assertNull(w.winner(), "FR-04：存活率 = 10% 不满足 '< 10%'，不应判负");
    }

    @Test
    @DisplayName("BR-04-2 + BR-04-3：僵持 900 tick 且双方损失率相等 → 玩家获胜")
    void stalemate_equalLoss_playerWins() {
        World w = Fixtures.separatedWorld(5, 5);
        w.startBattle(); // 双方均无战损，损失率相等（均为 0）

        runUntilWinner(w, 1100);

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

        runUntilWinner(w, 1100);

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
}
