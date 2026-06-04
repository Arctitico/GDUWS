package com.gduws.model;

/**
 * 单位残骸：单位死亡后在地图上留下的残骸标记
 * 仅保存位置、朝向和残骸贴图路径，供渲染层在战斗结束后清除
 */
public class Wreckage {

    public final double x;
    public final double y;
    public final double facing;
    public final String deadSpritePath;

    public Wreckage(double x, double y, double facing, String deadSpritePath) {
        this.x = x;
        this.y = y;
        this.facing = facing;
        this.deadSpritePath = deadSpritePath;
    }
}
