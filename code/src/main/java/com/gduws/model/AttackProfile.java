package com.gduws.model;

/**
 * 攻击域（攻击克制规则）。借鉴 RustedWarfare 的攻击开关，扩展为四个布尔位精确表达克制关系。
 */
public class AttackProfile {

    public boolean canAttackLand;         // 打地面
    public boolean canAttackWaterSurface; // 打水面（战列舰/驱逐舰）
    public boolean canAttackAir;          // 打空中
    public boolean canAttackUnderwater;   // 打水下（潜艇）
    public int     maxAttackRange;        // 最大射程（像素）
    public int     directDamage;          // 直接伤害
    public int     shootDelay;            // 射击冷却（tick）

    /** 弹种：子弹（快、单体）或炮弹（慢、群体）；默认子弹 */
    public ProjectileType projectileType = ProjectileType.BULLET;
    /** 射弹飞行速度（像素/tick）；炮弹通常远小于子弹 */
    public double projectileSpeed = 8.0;
    /** 群体伤害半径（像素）；仅炮弹生效，落点该半径内敌方单位按距离线性衰减受伤 */
    public int    splashRadius = 0;

    /** 是否拥有任何攻击能力。 */
    public boolean canAttackAnything() {
        return canAttackLand || canAttackWaterSurface || canAttackAir || canAttackUnderwater;
    }

    /** 判断本攻击域能否命中目标单位。 */
    public boolean canTarget(Unit target) {
        switch (target.layer()) {
            case LAND:       return canAttackLand;
            case WATER:      return canAttackWaterSurface;
            case AIR:        return canAttackAir;
            case UNDERWATER: return canAttackUnderwater;
            default:         return false;
        }
    }
}
