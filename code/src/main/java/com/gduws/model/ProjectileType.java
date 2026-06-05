package com.gduws.model;

/**
 * 弹种：决定射弹的飞行速度与是否产生群体伤害
 * <ul>
 *   <li>{@link #BULLET} 子弹：飞行速度快、命中后仅对单一目标结算伤害</li>
 *   <li>{@link #SHELL} 炮弹：飞行速度慢、落点周围产生群体（范围）伤害</li>
 * </ul>
 * 具体伤害量由发射单位的 {@link AttackProfile#directDamage} 决定，弹种本身不携带伤害值
 */
public enum ProjectileType {
    BULLET,
    SHELL
}
