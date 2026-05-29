package com.gduws.model;

/**
 * 单位所处高度层，由 {@link MovementType} 推导，供攻击域 {@link AttackProfile#canTarget} 使用。
 */
public enum UnitLayer {
    LAND,
    WATER,
    AIR,
    UNDERWATER;

    /** 由移动域推导所处层。 */
    public static UnitLayer fromMovementType(MovementType mt) {
        switch (mt) {
            case LAND:       return LAND;
            case WATER:      return WATER;
            case AIR:        return AIR;
            case UNDERWATER: return UNDERWATER;
            default:         return LAND;
        }
    }
}
