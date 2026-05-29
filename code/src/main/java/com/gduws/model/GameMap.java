package com.gduws.model;

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
        TerrainType t = tiles[cy][cx].terrain;
        switch (mt) {
            case LAND:       return t == TerrainType.PLAIN;
            case WATER:
            case UNDERWATER: return t == TerrainType.WATER;
            default:         return false;
        }
    }

    public int toCol(double px) { return (int) (px / tileSize); }

    public int toRow(double py) { return (int) (py / tileSize); }

    /** 格中心的像素 X。 */
    public double cellCenterX(int cx) { return cx * tileSize + tileSize / 2.0; }

    /** 格中心的像素 Y。 */
    public double cellCenterY(int cy) { return cy * tileSize + tileSize / 2.0; }

    public int pixelWidth()  { return cols * tileSize; }

    public int pixelHeight() { return rows * tileSize; }
}
