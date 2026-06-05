package com.gduws.model;

import java.util.Iterator;

/**
 * 射弹系统（FR-21）：推进所有飞行中的射弹，到达落点时结算伤害
 * <ul>
 *   <li>每 tick 沿锁定方向以 {@code speed} 前进；当与落点距离 ≤ 一步时判定命中</li>
 *   <li>子弹（BULLET）：仅对落点处的单一敌方目标结算 {@code damage}，命中即移除</li>
 *   <li>炮弹（SHELL）：对落点 {@code splashRadius} 内全部敌方单位按距离线性衰减结算伤害，
 *       并保留一段时间播放爆炸特效后移除</li>
 * </ul>
 * 群体伤害仅波及敌方阵营，不误伤发射方友军
 */
public final class ProjectileSystem {

    /** 炮弹爆炸特效持续 tick 数（供渲染层绘制扩散环） */
    public static final int EXPLOSION_DURATION = 12;

    public void update(World w) {
        Iterator<Projectile> it = w.projectiles.iterator();
        while (it.hasNext()) {
            Projectile p = it.next();

            // 已爆炸的炮弹仅推进特效计时，到期移除
            if (p.exploded) {
                p.explosionAge++;
                if (p.explosionAge >= EXPLOSION_DURATION) {
                    it.remove();
                }
                continue;
            }

            double dx = p.tx - p.x;
            double dy = p.ty - p.y;
            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist <= p.speed || dist < 1e-6) {
                // 到达落点：结算伤害
                p.x = p.tx;
                p.y = p.ty;
                detonate(w, p);
                p.exploded = true;
                if (!p.isSplash()) {
                    // 子弹无爆炸特效，立即移除
                    it.remove();
                }
            } else {
                p.x += dx / dist * p.speed;
                p.y += dy / dist * p.speed;
            }
        }
    }

    /** 命中结算：子弹单体、炮弹群体（线性衰减、仅敌方） */
    private void detonate(World w, Projectile p) {
        if (p.isSplash()) {
            double r = p.splashRadius;
            double r2 = r * r;
            for (Unit u : w.units) {
                if (u.faction == p.faction || u.isDead()) continue;
                double ux = u.x - p.x;
                double uy = u.y - p.y;
                double d2 = ux * ux + uy * uy;
                if (d2 > r2) continue;
                // 线性衰减：中心满伤，边缘递减（至少保留 25% 伤害避免边缘几乎无效）
                double d = Math.sqrt(d2);
                double falloff = 1.0 - (d / r) * 0.75;
                int dmg = (int) Math.round(p.damage * falloff);
                if (dmg > 0) u.hp -= dmg;
            }
        } else {
            // 子弹：对落点处最近的可命中敌方单位结算单体伤害
            Unit target = nearestEnemyAt(w, p);
            if (target != null) target.hp -= p.damage;
        }
    }

    /** 落点处与之重叠（或最近、半径内）的敌方单位，用于子弹单体结算 */
    private Unit nearestEnemyAt(World w, Projectile p) {
        Unit best = null;
        double bestD2 = Double.MAX_VALUE;
        for (Unit u : w.units) {
            if (u.faction == p.faction || u.isDead()) continue;
            double ux = u.x - p.x;
            double uy = u.y - p.y;
            double d2 = ux * ux + uy * uy;
            // 命中容差：单位半径外再放宽几像素，避免高速子弹擦过判定失败
            double hit = u.def.radius + 4.0;
            if (d2 <= hit * hit && d2 < bestD2) {
                bestD2 = d2;
                best = u;
            }
        }
        return best;
    }
}
