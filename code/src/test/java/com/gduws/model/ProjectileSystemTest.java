package com.gduws.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.testkit.Fixtures;

/**
 * {@link ProjectileSystem} 射弹推进与命中结算单元测试（FR-21）。
 *
 * <p>覆盖：子弹单体伤害、炮弹群体线性衰减、群体伤害不误伤友军、爆炸特效寿命、未到落点先飞行。</p>
 */
@DisplayName("ProjectileSystem 射弹结算（FR-21）")
class ProjectileSystemTest {

    private final ProjectileSystem system = new ProjectileSystem();

    private World worldWith(Unit... units) {
        World w = new World(Fixtures.landMap(40, 40));
        for (Unit u : units) {
            w.addUnit(u);
        }
        return w;
    }

    /** 已到落点的子弹（x/y == tx/ty），update 时立即结算。 */
    private Projectile bulletAt(Faction f, int dmg, double x, double y) {
        return new Projectile(ProjectileType.BULLET, f, dmg, 0, x, y, x, y, 8.0);
    }

    /** 已到落点的炮弹（群体伤害）。 */
    private Projectile shellAt(Faction f, int dmg, int splash, double x, double y) {
        return new Projectile(ProjectileType.SHELL, f, dmg, splash, x, y, x, y, 3.0);
    }

    @Test
    @DisplayName("子弹命中落点处单一敌人并扣血，命中后移除")
    void bulletHitsSingleTarget() {
        Unit target = Fixtures.unit(Fixtures.landTank(), Faction.ENEMY, 200, 200); // hp 210
        World w = worldWith(target);
        w.addProjectile(bulletAt(Faction.PLAYER, 50, 200, 200));

        system.update(w);

        assertEquals(160, target.hp);
        assertTrue(w.projectiles.isEmpty(), "子弹结算后立即移除");
    }

    @Test
    @DisplayName("子弹落点附近无敌人 → 不造成伤害")
    void bulletMissesWhenNoEnemyNear() {
        Unit target = Fixtures.unit(Fixtures.landTank(), Faction.ENEMY, 400, 400);
        World w = worldWith(target);
        w.addProjectile(bulletAt(Faction.PLAYER, 50, 200, 200)); // 落点远离目标

        system.update(w);

        assertEquals(210, target.hp);
        assertTrue(w.projectiles.isEmpty());
    }

    @Test
    @DisplayName("炮弹群体伤害按距离线性衰减（中心满伤、边缘递减）")
    void shellSplashFallsOffWithDistance() {
        Unit center = Fixtures.unit(Fixtures.landTank(), Faction.ENEMY, 200, 200); // d=0
        Unit edge = Fixtures.unit(Fixtures.landTank(), Faction.ENEMY, 220, 200);   // d=20，半径 40
        World w = worldWith(center, edge);
        w.addProjectile(shellAt(Faction.PLAYER, 40, 40, 200, 200));

        system.update(w);

        assertEquals(210 - 40, center.hp, "中心满伤 40");
        // falloff = 1 - (20/40)*0.75 = 0.625 → round(40*0.625) = 25
        assertEquals(210 - 25, edge.hp, "边缘线性衰减后 25");
    }

    @Test
    @DisplayName("炮弹群体伤害仅波及敌方，不误伤友军")
    void shellDoesNotHitFriendly() {
        Unit friendly = Fixtures.unit(Fixtures.landTank(), Faction.PLAYER, 200, 200);
        Unit enemy = Fixtures.unit(Fixtures.landTank(), Faction.ENEMY, 205, 200);
        World w = worldWith(friendly, enemy);
        w.addProjectile(shellAt(Faction.PLAYER, 40, 40, 200, 200));

        system.update(w);

        assertEquals(210, friendly.hp, "友军不应受群体伤害");
        assertTrue(enemy.hp < 210, "敌方应受群体伤害");
    }

    @Test
    @DisplayName("炮弹爆炸特效持续固定 tick 后移除")
    void shellExplosionExpiresAfterDuration() {
        Unit enemy = Fixtures.unit(Fixtures.landTank(), Faction.ENEMY, 200, 200);
        World w = worldWith(enemy);
        w.addProjectile(shellAt(Faction.PLAYER, 40, 40, 200, 200));

        system.update(w); // 结算 + 进入爆炸特效
        assertEquals(1, w.projectiles.size(), "炮弹保留以播放爆炸特效");
        assertTrue(w.projectiles.get(0).exploded);

        for (int i = 0; i < ProjectileSystem.EXPLOSION_DURATION; i++) {
            system.update(w);
        }
        assertTrue(w.projectiles.isEmpty(), "特效到期后移除");
    }

    @Test
    @DisplayName("未到落点的射弹按速度沿直线前进，尚不结算")
    void projectileTravelsBeforeArrival() {
        World w = worldWith();
        Projectile p = new Projectile(ProjectileType.BULLET, Faction.PLAYER, 10, 0, 0, 0, 100, 0, 10.0);
        w.addProjectile(p);

        system.update(w);

        assertEquals(10.0, p.x, 1e-9, "前进一个 speed 的距离");
        assertFalse(p.exploded);
        assertEquals(1, w.projectiles.size());
    }
}
