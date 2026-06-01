package com.gduws.view;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import com.gduws.model.Decoration;
import com.gduws.model.TerrainType;

/**
 * 地形纹理库：从复用自 RustedWarfare 的图块集（atlas）中裁切 20×20 子图，
 * 为每种地形提供可平铺的填充纹理，为每种装饰物提供叠加贴图。结果均做缓存
 *
 * <p>缺失贴图时返回 null，由渲染器回退到纯色块，保证无素材也能运行</p>
 */
final class TerrainTextures {

    /** 图块集原始单格尺寸（像素），与 RustedWarfare 一致 */
    private static final int SRC = 20;
    private static final String DIR = "assets/tilesets/";

    /** atlas 内子图定位：文件名 + 列 + 行 */
    private static final class Cell {
        final String file; final int col; final int row;
        Cell(String file, int col, int row) { this.file = file; this.col = col; this.row = row; }
    }

    private static final Map<TerrainType, Cell> TERRAIN = new EnumMap<>(TerrainType.class);
    private static final Map<Decoration, Cell> DECORATION = new EnumMap<>(Decoration.class);
    static {
        TERRAIN.put(TerrainType.GRASS,    new Cell("fauna_highland.png", 0, 1));
        TERRAIN.put(TerrainType.DIRT,     new Cell("dirt.png", 0, 0));
        TERRAIN.put(TerrainType.SAND,     new Cell("sand.png", 0, 0));
        TERRAIN.put(TerrainType.MOUNTAIN, new Cell("stone.png", 1, 1));
        TERRAIN.put(TerrainType.SHALLOW,  new Cell("shallowwater.png", 0, 0));
        TERRAIN.put(TerrainType.WATER,    new Cell("water.png", 0, 0));
        TERRAIN.put(TerrainType.DEEP,     new Cell("deepwater.png", 0, 0));

        DECORATION.put(Decoration.FLOWER, new Cell("fauna_highland.png", 2, 0));
        DECORATION.put(Decoration.TREE,   new Cell("fauna_highland.png", 4, 4));
        DECORATION.put(Decoration.TREE2,  new Cell("fauna_highland.png", 3, 4));
        DECORATION.put(Decoration.BUSH,   new Cell("decoration.png", 6, 2));
        DECORATION.put(Decoration.ROCK,   new Cell("decoration.png", 2, 1));
    }

    private final Map<String, BufferedImage> atlasCache = new HashMap<>();
    private final Map<String, BufferedImage> tileCache = new HashMap<>();
    private final Map<String, Boolean> missing = new HashMap<>();

    BufferedImage terrain(TerrainType t) {
        return sub(TERRAIN.get(t));
    }

    BufferedImage decoration(Decoration d) {
        return sub(DECORATION.get(d));
    }

    private BufferedImage sub(Cell cell) {
        if (cell == null) return null;
        String key = cell.file + "@" + cell.col + "," + cell.row;
        BufferedImage cached = tileCache.get(key);
        if (cached != null) return cached;
        if (Boolean.TRUE.equals(missing.get(key))) return null;

        BufferedImage atlas = atlas(cell.file);
        if (atlas == null) { missing.put(key, true); return null; }
        int x = cell.col * SRC;
        int y = cell.row * SRC;
        int w = Math.min(SRC, atlas.getWidth() - x);
        int h = Math.min(SRC, atlas.getHeight() - y);
        if (x < 0 || y < 0 || w <= 0 || h <= 0) { missing.put(key, true); return null; }
        BufferedImage img = atlas.getSubimage(x, y, w, h);
        tileCache.put(key, img);
        return img;
    }

    private BufferedImage atlas(String file) {
        BufferedImage img = atlasCache.get(file);
        if (img != null) return img;
        if (Boolean.TRUE.equals(missing.get(file))) return null;
        try {
            File f = new File(DIR + file);
            if (f.exists()) {
                img = ImageIO.read(f);
            }
        } catch (Exception e) {
            img = null;
        }
        if (img == null) {
            missing.put(file, true);
            return null;
        }
        atlasCache.put(file, img);
        return img;
    }
}
