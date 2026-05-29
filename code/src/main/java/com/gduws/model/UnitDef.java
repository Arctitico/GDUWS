package com.gduws.model;

/**
 * 单位静态定义。由 JSON 加载，全程只读、可被多个 {@link Unit} 实例共享（享元）。
 */
public class UnitDef {

    public String       id;          // 唯一标识，如 "light_tank"
    public String       displayName; // 显示名
    public int          maxHp;
    public double       radius;       // 碰撞/选择半径
    public MovementType movementType;
    public double       moveSpeed;    // 每 tick 位移
    public int          sightRange;   // 视野半径（侦察核心）
    public UnitRole     role;
    public AttackProfile attack;
    public String       spritePath;   // 复用 RustedWarfare 的 PNG（可为空）
}
