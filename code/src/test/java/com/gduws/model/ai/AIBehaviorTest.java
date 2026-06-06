package com.gduws.model.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.model.Faction;
import com.gduws.model.GameMap;
import com.gduws.model.Unit;
import com.gduws.model.UnitRole;
import com.gduws.model.UnitState;
import com.gduws.model.World;
import com.gduws.testkit.Fixtures;

/**
 * AI 行为单元测试：{@link AISystem} 角色自动转换与 {@link StrikeAI} 状态机要点。
 *
 * <p>关联：BR-03-6（打击空闲 240 tick 自动转侦察）、BR-03-7（撤退方向为敌方质心反方向）、
 * FR-03 验收（打击 IDLE → 情报板有目标 → MOVING_TO_TARGET）。</p>
 */
@DisplayName("AI 行为（AISystem / StrikeAI）")
class AIBehaviorTest {

    @Test
    @DisplayName("BR-03-6：打击单位连续 IDLE 满 240 tick 后自动转为侦察")
    void idleStrikeConvertsToScoutAfter240Ticks() {
        World w = new World(Fixtures.landMap(30, 30));
        Unit u = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 15, 15);
        w.addUnit(u); // 无敌人 → 一直 IDLE

        for (int i = 0; i < 239; i++) {
            w.tick();
        }
        assertEquals(UnitRole.STRIKE, u.role, "第 239 tick 仍应为打击");

        w.tick(); // 第 240 tick
        assertEquals(UnitRole.SCOUT, u.role, "满 240 tick 转为侦察");
        assertEquals(UnitState.SCOUTING, u.state);
        assertTrue(u.autoScoutFromStrike, "标记为'由打击自动转侦察'");
    }

    @Test
    @DisplayName("自动转侦察的单位：情报板一旦出现敌人立即转回打击")
    void autoScoutRevertsToStrikeWhenEnemyKnown() {
        World w = new World(Fixtures.landMap(30, 30));
        Unit u = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 15, 15);
        u.role = UnitRole.SCOUT;
        u.autoScoutFromStrike = true;
        w.addUnit(u);

        Unit enemy = Fixtures.unit(Fixtures.landTank(), Faction.ENEMY, 400, 400);
        w.intelOf(Faction.PLAYER).report(enemy, enemy.x, enemy.y, 0);

        new AISystem().update(w);

        assertEquals(UnitRole.STRIKE, u.role);
        assertFalse(u.autoScoutFromStrike);
    }

    @Test
    @DisplayName("打击单位情报板无敌人时保持 IDLE、无目标无路径")
    void strikeIdleWithoutIntel() {
        World w = new World(Fixtures.landMap(30, 30));
        Unit u = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 15, 15);
        w.addUnit(u);

        new StrikeAI().update(u, w);

        assertEquals(UnitState.IDLE, u.state);
        assertNull(u.path);
        assertNull(u.currentTarget);
    }

    @Test
    @DisplayName("FR-03：情报板出现可攻击目标 → 打击单位转入 MOVING_TO_TARGET 并锁定目标")
    void strikeMovesToTargetFromSharedIntel() {
        World w = new World(Fixtures.landMap(40, 40));
        Unit u = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 5, 10);   // (110,210)
        Unit enemy = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.ENEMY, 20, 10); // (410,210)，距 300 > 追击阈值
        w.addUnit(u);
        w.addUnit(enemy);
        w.intelOf(Faction.PLAYER).report(enemy, enemy.x, enemy.y, 0);

        new StrikeAI().update(u, w);

        assertEquals(UnitState.MOVING_TO_TARGET, u.state);
        assertSame(enemy, u.currentTarget);
    }

    @Test
    @DisplayName("BR-03-7：兵力悬殊时向敌方质心反方向撤退")
    void retreatDirectionIsAwayFromEnemyCentroid() {
        GameMap m = Fixtures.landMap(50, 50);
        World w = new World(m);
        Unit u = Fixtures.unit(Fixtures.landTank(), Faction.PLAYER, 300, 300);
        u.hp = 10; // 我方极弱
        w.addUnit(u);

        // 两个强敌位于西侧（质心约 x=210），撤退应朝东
        Unit a = Fixtures.unit(Fixtures.landTank(), Faction.ENEMY, 200, 300);
        Unit b = Fixtures.unit(Fixtures.landTank(), Faction.ENEMY, 220, 300);
        w.addUnit(a);
        w.addUnit(b);
        w.intelOf(Faction.PLAYER).report(a, a.x, a.y, 0);
        w.intelOf(Faction.PLAYER).report(b, b.x, b.y, 0);

        new StrikeAI().update(u, w);

        assertEquals(UnitState.RETREATING, u.state);
        assertNotNull(u.moveGoal);
        assertTrue(u.moveGoal.x > m.toCol(300), "撤退目标应朝东，远离西侧敌人");
        assertNull(u.currentTarget, "撤退时放弃当前目标");
    }
}
