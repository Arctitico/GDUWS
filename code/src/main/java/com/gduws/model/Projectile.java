package com.gduws.model;

/**
 * 飞行中的射弹实体（FR-21）：由攻击单位发射，沿直线飞向目标坐标，命中后结算伤害
 * <p>射弹只携带飞行参数与结算参数，不直接修改单位状态——伤害结算由 {@link ProjectileSystem} 完成</p>
 */
public class Projectile {

    public final ProjectileType type;
    public final Faction faction;       // 发射方阵营（炮弹群体伤害据此排除友军）
    public final int     damage;        // 命中伤害（来自发射单位 directDamage）
    public final int     splashRadius;  // 群体伤害半径（像素），仅炮弹 > 0

    public double x, y;                 // 当前坐标
    public double facing;               // 飞行朝向（弧度），供渲染
    public final double tx, ty;         // 目标落点坐标（发射瞬间锁定）
    public final double speed;          // 飞行速度（像素/tick）

    public boolean exploded = false;    // 是否已到达落点并结算
    public int     explosionAge = 0;    // 爆炸特效已持续 tick 数

    public Projectile(ProjectileType type, Faction faction, int damage, int splashRadius,
                      double x, double y, double tx, double ty, double speed) {
        this.type = type;
        this.faction = faction;
        this.damage = damage;
        this.splashRadius = splashRadius;
        this.x = x;
        this.y = y;
        this.tx = tx;
        this.ty = ty;
        this.speed = speed;
        this.facing = Math.atan2(ty - y, tx - x);
    }

    /** 是否为群体伤害炮弹 */
    public boolean isSplash() {
        return type == ProjectileType.SHELL && splashRadius > 0;
    }
}
