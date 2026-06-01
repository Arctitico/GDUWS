package com.gduws.model;

import java.awt.Point;

/** 网格地图：持有地形、提供可通行性判断与像素/格坐标换算。 */
public class GameMap {

    public final int cols;
    public final int rows;
    public final int tileSize;     // 像素/格，取 20 与参考项目一致
    private final Tile[][] tiles;  // [row][col]

    public GameMap(int cols, int rows, int tileSize, Tile[][] tiles) {
        this.cols = cols;
        this.rows = rows;
        this.tileSize = tileSize;
        this.tiles = tiles;
    }

    public Tile tileAt(int cx, int cy) {
        return tiles[cy][cx];
    }

    public boolean inBounds(int cx, int cy) {
        return cx >= 0 && cy >= 0 && cx < cols && cy < rows;
    }

    /** 指定移动域能否通行该格。 */
    public boolean isPassable(int cx, int cy, MovementType mt) {
        if (mt == MovementType.AIR) {
            return inBounds(cx, cy); // 空中无视地形，只要在界内
        }
        if (!inBounds(cx, cy)) {
            return false;
        }
        TerrainType.Pass pass = tiles[cy][cx].terrain.pass;
        switch (mt) {
            case LAND:       return pass == TerrainType.Pass.LAND;
            case WATER:
            case UNDERWATER: return pass == TerrainType.Pass.WATER;
            default:         return false;
        }
    }

    /** 布兵阶段：玩家能否在该格放置单位（既要地形可通行，又要未被标记为禁布区）。 */
    public boolean isDeployable(int cx, int cy, MovementType mt) {
        return isPassable(cx, cy, mt) && inBounds(cx, cy) && tiles[cy][cx].deployable;
    }

    /** 该格是否被标记为禁止布兵（不含地形判断，供渲染禁布区蒙版使用）。 */
    public boolean isDeployForbidden(int cx, int cy) {
        return inBounds(cx, cy) && !tiles[cy][cx].deployable;
    }

    public int toCol(double px) { return (int) (px / tileSize); }

    public int toRow(double py) { return (int) (py / tileSize); }

    /** 格中心的像素 X。 */
    public double cellCenterX(int cx) { return cx * tileSize + tileSize / 2.0; }

    /** 格中心的像素 Y。 */
    public double cellCenterY(int cy) { return cy * tileSize + tileSize / 2.0; }

    public int pixelWidth()  { return cols * tileSize; }

    public int pixelHeight() { return rows * tileSize; }

    /** 在 (cx,cy) 周围由近及远扩展，找最近的可通行格；找不到返回 null */
    public Point findNearestPassable(int cx, int cy, MovementType mt, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = cx + dx;
                    int ny = cy + dy;
                    if (isPassable(nx, ny, mt)) {
                        return new Point(nx, ny);
                    }
                }
            }
        }
        return null;
    }
}
