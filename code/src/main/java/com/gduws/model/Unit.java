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
    public double facing;          // 朝向（弧度）
    public int    hp;
    public UnitState state = UnitState.IDLE;
    public int    shootCooldown = 0;
    public Unit   currentTarget;   // 当前攻击目标
    public Deque<Point> path;      // 当前路径
    public Point  moveGoal;        // 移动终点（格）

    public Unit(UnitDef def, Faction faction, double x, double y) {
        this.def = def;
        this.faction = faction;
        this.x = x;
        this.y = y;
        this.hp = def.maxHp;
    }

    public boolean isDead()  { return hp <= 0; }

    public boolean isScout() { return def.role == UnitRole.SCOUT; }

    /** 由移动域推导所处高度层。 */
    public UnitLayer layer() {
        return UnitLayer.fromMovementType(def.movementType);
    }
}
