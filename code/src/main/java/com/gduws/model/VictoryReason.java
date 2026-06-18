package com.gduws.model;

/**
 * 战斗结算原因（FR-04）：用于在结算界面准确说明本局是如何结束的，而非笼统的"胜/负"。
 *
 * <ul>
 *   <li>{@link #ANNIHILATION} —— 失败方单位被全部歼灭（存活数归零）。</li>
 *   <li>{@link #ATTRITION} —— 失败方损失超过 90%（仍有零星残存），丧失战斗力。</li>
 *   <li>{@link #STALEMATE} —— 双方长时间无兵力损失（僵持超时），按双方战损率提前判定。</li>
 * </ul>
 */
public enum VictoryReason {
    ANNIHILATION,
    ATTRITION,
    STALEMATE
}
