package com.gduws.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.testkit.Fixtures;

/**
 * {@link CombatSystem} 选目标 / 瞄准 / 开火（生成射弹）单元测试。
 *
 * <p>关联：BR-03-5（选最近敌人、瞄准、冷却就绪扣血）、FR-21（开火生成飞行射弹，伤害延迟到落点结算）。
 * 目标统一用无武装单位，避免其反击干扰射弹计数。</p>
 */
@DisplayName("CombatSystem 战斗结算")
class CombatSystemTest {

    private World worldWith(Unit... units) {
        World w = new World(Fixtures.landMap(40, 40));
        for (Unit u : units) {
            w.addUnit(u);
        }
        return w;
    }

    @Test
    @DisplayName("射程内敌人 → 锁定目标并发射一枚射弹，进入冷却（FR-21）")
    void firesProjectileAtInRangeEnemy() {
        Unit attacker = Fixtures.unit(Fixtures.landTank(), Faction.PLAYER, 110, 110); // 射程 130，伤害 25
        Unit enemy = Fixtures.unit(Fixtures.unarmedLand(), Faction.ENEMY, 180, 110);  // 距离 70
        World w = worldWith(attacker, enemy);

        w.combatSystem().update(w);

        assertSame(enemy, attacker.currentTarget);
        assertEquals(1, w.projectiles.size(), "应发射一枚射弹");
        assertEquals(1, w.combatSystem().recentShots.size());
        assertEquals(75, attacker.shootCooldown, "开火后进入冷却");

        Projectile p = w.projectiles.get(0);
        assertEquals(Faction.PLAYER, p.faction);
        assertEquals(25, p.damage);
        assertEquals(enemy.x, p.tx, 1e-9);
        assertEquals(enemy.y, p.ty, 1e-9);
        assertEquals(ProjectileType.BULLET, p.type);
    }

    @Test
    @DisplayName("无炮塔单位整体转向目标")
    void aimsBodyTowardTarget() {
        Unit attacker = Fixtures.unit(Fixtures.landTank(), Faction.PLAYER, 110, 110);
        Unit enemy = Fixtures.unit(Fixtures.unarmedLand(), Faction.ENEMY, 110, 210); // 正南，aim = +π/2
        World w = worldWith(attacker, enemy);

        w.combatSystem().update(w);

        assertEquals(Math.PI / 2, attacker.turretFacing, 1e-6);
        assertEquals(Math.PI / 2, attacker.facing, 1e-6);
    }

    @Test
    @DisplayName("冷却未就绪时不重复开火")
    void cooldownPreventsImmediateRefire() {
        Unit attacker = Fixtures.unit(Fixtures.landTank(), Faction.PLAYER, 110, 110);
        Unit enemy = Fixtures.unit(Fixtures.unarmedLand(), Faction.ENEMY, 180, 110);
        World w = worldWith(attacker, enemy);

        w.combatSystem().update(w); // 第一发
        w.combatSystem().update(w); // 冷却中

        assertEquals(1, w.projectiles.size(), "冷却期间不应再发射");
        assertEquals(74, attacker.shootCooldown, "冷却每 tick 递减");
        assertEquals(0, w.combatSystem().recentShots.size(), "本 tick 无新开火事件");
    }

    @Test
    @DisplayName("多个敌人时选最近者")
    void picksNearestEnemy() {
        Unit attacker = Fixtures.unit(Fixtures.landTank(), Faction.PLAYER, 110, 110);
        Unit near = Fixtures.unit(Fixtures.unarmedLand(), Faction.ENEMY, 160, 110); // 距离 50
        Unit far = Fixtures.unit(Fixtures.unarmedLand(), Faction.ENEMY, 210, 110);  // 距离 100
        World w = worldWith(attacker, near, far);

        w.combatSystem().update(w);

        assertSame(near, attacker.currentTarget);
    }

    @Test
    @DisplayName("目标超出射程 → 不开火")
    void noFireWhenOutOfRange() {
        Unit attacker = Fixtures.unit(Fixtures.landTank(), Faction.PLAYER, 110, 110);
        Unit enemy = Fixtures.unit(Fixtures.unarmedLand(), Faction.ENEMY, 110, 310); // 距离 200 > 130
        World w = worldWith(attacker, enemy);

        w.combatSystem().update(w);

        assertNull(attacker.currentTarget);
        assertTrue(w.projectiles.isEmpty());
    }

    @Test
    @DisplayName("攻击域不可命中的目标层 → 不开火（轻坦克打不到空中）")
    void noFireAgainstUntargetableLayer() {
        Unit attacker = Fixtures.unit(Fixtures.landTank(), Faction.PLAYER, 110, 110); // 不能打空
        Unit airEnemy = Fixtures.unit(Fixtures.layerTarget("air", MovementType.AIR), Faction.ENEMY, 160, 110);
        World w = worldWith(attacker, airEnemy);

        w.combatSystem().update(w);

        assertNull(attacker.currentTarget);
        assertTrue(w.projectiles.isEmpty());
    }
}
