package com.gduws.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.model.Faction;
import com.gduws.model.Unit;
import com.gduws.model.UnitRole;
import com.gduws.model.World;
import com.gduws.testkit.Fixtures;

/**
 * 集成测试：完整自动战斗主循环推演至结算（FR-03 全流程 + FR-04 结算 + NFR-08 不中断）。
 *
 * <p>玩家 8 单位（含 1 侦察）压制敌方 2 单位，在同一开阔地图上自动推演。
 * 验证：整个 tick 主循环（视野→AI→移动→战斗→射弹→清理→胜负）能稳定运行至产生胜者且不抛异常。</p>
 */
@DisplayName("集成：完整自动战斗推演")
class FullBattleIT {

    @Test
    @DisplayName("8 v 2 自动推演稳定运行并以玩家获胜结束")
    void battleRunsToPlayerVictory() {
        World w = new World(Fixtures.landMap(25, 25));

        // 玩家：1 侦察（引导发现）+ 7 打击，聚集在西侧
        Unit scout = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 2, 12);
        scout.role = UnitRole.SCOUT;
        w.addUnit(scout);
        int[][] strikeCells = {{2, 11}, {2, 13}, {3, 11}, {3, 12}, {3, 13}, {2, 14}, {3, 14}};
        for (int[] cell : strikeCells) {
            w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, cell[0], cell[1]));
        }
        // 敌方：2 打击，聚集在东侧
        w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.ENEMY, 21, 12));
        w.addUnit(Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.ENEMY, 22, 12));

        w.startBattle();

        // NFR-08：主循环不应抛异常；推进直到产生胜者
        Faction winner = assertDoesNotThrow(() -> {
            for (int t = 0; t < 10000; t++) {
                w.tick();
                if (w.winner() != null) {
                    return w.winner();
                }
            }
            return w.winner();
        });

        assertNotNull(winner, "战斗应在合理时间内结算");
        assertEquals(Faction.PLAYER, winner, "8 v 2 应由玩家获胜");
    }
}
