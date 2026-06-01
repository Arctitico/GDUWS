package com.gduws.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.gduws.model.Decoration;
import com.gduws.model.GameMap;
import com.gduws.model.Tile;
import com.gduws.model.TerrainType;

/**
 * 解析分层字符网格地图。
 *
 * <p>首个非注释行为 {@code cols rows tileSize}。其后内容按 {@code [section]} 分层，
 * 支持三层（均可省略，省略则取默认值）：</p>
 * <ul>
 *   <li>{@code [terrain]}（默认层）：{@code .}=草地 {@code ,}=泥地 {@code s}=沙地
 *       {@code #}=山地 {@code _}=浅水 {@code ~}=水域 {@code =}=深水</li>
 *   <li>{@code [decoration]}：{@code T}=树 {@code t}=树(变体) {@code b}=灌木 {@code R}=岩石，{@code .}=无</li>
 *   <li>{@code [deploy]}：{@code X}=禁止布兵，{@code .}=允许</li>
 * </ul>
 *
 * <p>每层恰好 {@code rows} 行。以 {@code #} 开头的注释仅允许出现在头部行之前
 * （地图数据中 {@code #} 表示山地）。旧版仅含地形单层的地图依然兼容。</p>
 */
public class MapLoader {

    private enum Section { TERRAIN, DECORATION, DEPLOY }

    public GameMap loadFile(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        int cols = -1, rows = -1, tileSize = 20;
        boolean headerParsed = false;
        Section section = Section.TERRAIN;
        List<String> terrainLines = new ArrayList<>();
        List<String> decorationLines = new ArrayList<>();
        List<String> deployLines = new ArrayList<>();

        for (String raw : lines) {
            if (raw.isBlank()) {
                continue;
            }
            if (!headerParsed) {
                String trimmed = raw.trim();
                if (trimmed.startsWith("#")) {
                    continue; // 头部之前的注释
                }
                String[] parts = trimmed.split("\\s+");
                cols = Integer.parseInt(parts[0]);
                rows = Integer.parseInt(parts[1]);
                if (parts.length >= 3) {
                    tileSize = Integer.parseInt(parts[2]);
                }
                headerParsed = true;
                continue;
            }
            // 分层标记（以 '[' 开头，不会与任何地形字符冲突）
            String marker = raw.trim().toLowerCase();
            if (marker.startsWith("[")) {
                if (marker.startsWith("[terrain")) {
                    section = Section.TERRAIN;
                } else if (marker.startsWith("[decoration")) {
                    section = Section.DECORATION;
                } else if (marker.startsWith("[deploy")) {
                    section = Section.DEPLOY;
                }
                continue;
            }
            switch (section) {
                case TERRAIN:    if (terrainLines.size()    < rows) terrainLines.add(raw);    break;
                case DECORATION: if (decorationLines.size() < rows) decorationLines.add(raw); break;
                case DEPLOY:     if (deployLines.size()     < rows) deployLines.add(raw);     break;
                default: break;
            }
        }

        if (!headerParsed) {
            throw new IOException("Map header (cols rows tileSize) not found: " + file);
        }
        if (terrainLines.size() < rows) {
            throw new IOException("Map has fewer terrain rows (" + terrainLines.size()
                + ") than declared (" + rows + "): " + file);
        }

        Tile[][] tiles = new Tile[rows][cols];
        for (int r = 0; r < rows; r++) {
            String tLine = terrainLines.get(r);
            String dLine = r < decorationLines.size() ? decorationLines.get(r) : "";
            String pLine = r < deployLines.size() ? deployLines.get(r) : "";
            for (int c = 0; c < cols; c++) {
                char tch = c < tLine.length() ? tLine.charAt(c) : '.';
                Tile tile = new Tile(terrainOf(tch));
                char dch = c < dLine.length() ? dLine.charAt(c) : '.';
                tile.decoration = decorationOf(dch);
                char pch = c < pLine.length() ? pLine.charAt(c) : '.';
                tile.deployable = (pch != 'X' && pch != 'x');
                tiles[r][c] = tile;
            }
        }
        return new GameMap(cols, rows, tileSize, tiles);
    }

    private static TerrainType terrainOf(char ch) {
        switch (ch) {
            case '#': return TerrainType.MOUNTAIN;
            case ',': return TerrainType.DIRT;
            case 's':
            case 'S': return TerrainType.SAND;
            case '_': return TerrainType.SHALLOW;
            case '~': return TerrainType.WATER;
            case '=': return TerrainType.DEEP;
            case '.':
            default:  return TerrainType.GRASS;
        }
    }

    private static Decoration decorationOf(char ch) {
        switch (ch) {
            case 'F': return Decoration.FLOWER;
            case 'T': return Decoration.TREE;
            case 't': return Decoration.TREE2;
            case 'b':
            case 'B': return Decoration.BUSH;
            case 'R':
            case 'r': return Decoration.ROCK;
            default:  return null;
        }
    }
}
