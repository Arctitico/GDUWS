package com.gduws.model;

/**
 * 地形类型。每种地形归属一个通行类别，决定哪些移动域可以进入
 *
 * <p>陆地类（GRASS/DIRT/SAND）仅 LAND 单位可通行；水域类（SHALLOW/WATER/DEEP）仅
 * WATER/UNDERWATER 单位可通行；MOUNTAIN 为不可通行障碍（仅 AIR 可越过）</p>
 */
public enum TerrainType {

    GRASS(Pass.LAND),      // 草地
    DIRT(Pass.LAND),       // 泥地
    SAND(Pass.LAND),       // 沙地
    MOUNTAIN(Pass.BLOCK),  // 山地/岩石，地面与水面均不可通行
    SHALLOW(Pass.WATER),   // 浅水
    WATER(Pass.WATER),     // 水域
    DEEP(Pass.WATER);      // 深水

    /** 通行类别 */
    public enum Pass { LAND, WATER, BLOCK }

    public final Pass pass;

    TerrainType(Pass pass) {
        this.pass = pass;
    }

    public boolean isLand()  { return pass == Pass.LAND; }
    public boolean isWater() { return pass == Pass.WATER; }
}
