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
