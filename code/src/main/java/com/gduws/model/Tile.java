package com.gduws.model;

/** 地图单元格：地形 + 可选装饰物 + 布兵许可属性 */
public class Tile {

    /** 基础地形（决定通行性与纹理） */
    public TerrainType terrain;
    /** 装饰物覆盖（纯表现，可为 null） */
    public Decoration decoration;
    /** 布兵阶段玩家能否在此格放置单位（用于划分禁布区） */
    public boolean deployable = true;

    public Tile(TerrainType terrain) {
        this.terrain = terrain;
    }
}
