package com.gduws.model;

import java.awt.Point;
import java.util.Deque;

/** 单位运行时实例：静态定义 + 动态状态。 */
public class Unit {

    // 静态
    public final UnitDef def;
    public final Faction faction;

    // 动态
    public double x, y;            // 战场坐标（像素）
    public double facing;          // 底座朝向（弧度），随移动改变
    public double turretFacing;    // 炮塔朝向（弧度），随攻击目标改变
    public int    hp;
    public UnitState state = UnitState.IDLE;
    public int    shootCooldown = 0;
    public Unit   currentTarget;   // 当前攻击目标
    public Deque<Point> path;      // 当前路径
    public Point  moveGoal;        // 移动终点（格）
    public int    lastActiveTick;  // 最近一次有效行动（移动/攻击/撤退）的 tick

    /** 任务角色（侦察 / 打击），可由玩家在布兵阶段为每个单位指派 */
    public UnitRole role;

    /** 是否由打击单位因长时间空闲而自动转为侦察；发现新敌人后据此转回打击 */
    public boolean autoScoutFromStrike = false;

    public Unit(UnitDef def, Faction faction, double x, double y) {
        this.def = def;
        this.faction = faction;
        this.x = x;
        this.y = y;
        this.turretFacing = facing;
        this.hp = def.maxHp;
        this.role = UnitRole.STRIKE;
    }

    public boolean isDead()  { return hp <= 0; }

    public boolean isScout() { return role == UnitRole.SCOUT; }

    /** 由移动域推导所处高度层。 */
    public UnitLayer layer() {
        return UnitLayer.fromMovementType(def.movementType);
    }
}
