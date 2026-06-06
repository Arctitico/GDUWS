package com.gduws.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.model.Faction;
import com.gduws.model.Unit;
import com.gduws.model.UnitRole;
import com.gduws.model.UnitState;
import com.gduws.model.World;
import com.gduws.testkit.Fixtures;

/**
 * 集成测试：侦察-打击协同链路闭环（成功标准 #2、BR-03-2）。
 *
 * <p>验证"侦察发现 → 情报板共享 → 打击单位据共享情报接敌"完整闭环：
 * 打击单位自身视野够不到敌人，却能凭本阵营情报板锁定并奔向侦察单位发现的目标。</p>
 */
@DisplayName("集成：侦察→情报→打击 协同链路")
class ReconStrikeChainIT {

    @Test
    @DisplayName("打击单位据侦察共享的情报锁定并接近视野外的敌人")
    void scoutDiscoveryDrivesStrikeEngagement() {
        World w = new World(Fixtures.landMap(40, 20)); // 800×400

        // 侦察兵贴近敌人（能目击）；打击兵远在西侧（自身视野够不到敌人）
        Unit scout = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 29, 10); // (590,210)
        scout.role = UnitRole.SCOUT;
        Unit strike = Fixtures.unitAtCell(w.map, Fixtures.landTank(), Faction.PLAYER, 5, 10); // (110,210)
        Unit enemy = Fixtures.unitAtCell(w.map, Fixtures.unarmedLand(), Faction.ENEMY, 30, 10); // (610,210)
        w.addUnit(scout);
        w.addUnit(strike);
        w.addUnit(enemy);

        // 初始：情报板为空
        assertFalse(w.intelOf(Faction.PLAYER).hasAnyEnemy());
        // 前提：打击单位距敌 500px，远超自身视野 120px——它本身"看不见"敌人
        double strikeStartX = strike.x;
        assertTrue(Math.hypot(strike.x - enemy.x, strike.y - enemy.y) > strike.def.sightRange,
                "前提：打击单位自身视野够不到敌人");

        for (int i = 0; i < 3; i++) {
            w.tick();
        }

        // 链路闭环：侦察写入情报板 → 打击据此进入接敌状态并朝它本看不见的敌人推进。
        // 注：currentTarget 由 CombatSystem 每 tick 按"是否在射程内"重置，目标未进射程时为 null，
        // 故此处以"接敌状态 + 朝敌移动"作为链路生效的判据。
        assertTrue(w.intelOf(Faction.PLAYER).hasAnyEnemy(), "侦察单位应已把敌情写入情报板");
        assertTrue(strike.state == UnitState.MOVING_TO_TARGET || strike.state == UnitState.ATTACKING,
                "打击单位应据共享情报进入接敌状态（自身视野够不到敌人）");
        assertTrue(strike.x > strikeStartX, "打击单位应朝敌人方向（东）推进");
    }
}
