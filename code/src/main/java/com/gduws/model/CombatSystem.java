package com.gduws.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 战斗系统：命中瞬时结算
 * <ul>
 *   <li>遍历有攻击能力的单位，按攻击域过滤、在射程内选最近敌人</li>
 *   <li>面向目标、冷却就绪时扣 {@code directDamage}，重置 {@code shootCooldown}</li>
 * </ul>
 */
public final class CombatSystem {

    /** 最近一帧产生的开火事件（供渲染层画攻击连线） */
    public final List<ShotEvent> recentShots = new ArrayList<>();

    public void update(World w) {
        recentShots.clear();
        for (Unit u : w.units) {
            if (u.isDead()) continue;
            if (u.shootCooldown > 0) u.shootCooldown--;
            AttackProfile ap = u.def.attack;
            if (ap == null || !ap.canAttackAnything()) continue;

            Unit target = pickTarget(u, w);
            if (target == null) {
                u.currentTarget = null;
                continue;
            }
            u.currentTarget = target;

            // 面向目标（瞬时转向，确保能开火）
            double dx = target.x - u.x;
            double dy = target.y - u.y;
            u.facing = Math.atan2(dy, dx);

            if (u.shootCooldown == 0) {
                target.hp -= ap.directDamage;
                u.shootCooldown = ap.shootDelay;
                recentShots.add(new ShotEvent(u.x, u.y, target.x, target.y, u.faction));
            }
        }
    }

    private Unit pickTarget(Unit u, World w) {
        AttackProfile ap = u.def.attack;
        double r2 = (double) ap.maxAttackRange * ap.maxAttackRange;
        Unit best = null;
        double bestD2 = Double.MAX_VALUE;
        for (Unit t : w.units) {
            if (t.faction == u.faction) continue;
            if (t.isDead()) continue;
            if (!ap.canTarget(t)) continue;
            double dx = t.x - u.x;
            double dy = t.y - u.y;
            double d2 = dx * dx + dy * dy;
            if (d2 <= r2 && d2 < bestD2) {
                bestD2 = d2;
                best = t;
            }
        }
        return best;
    }

    /** 一次开火事件（用于渲染瞬时连线） */
    public static final class ShotEvent {
        public final double sx, sy, tx, ty;
        public final Faction shooterFaction;

        public ShotEvent(double sx, double sy, double tx, double ty, Faction f) {
            this.sx = sx;
            this.sy = sy;
            this.tx = tx;
            this.ty = ty;
            this.shooterFaction = f;
        }
    }
}
