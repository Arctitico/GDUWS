package com.gduws.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.gduws.model.GameMap;
import com.gduws.model.Tile;
import com.gduws.model.TerrainType;

/**
 * 解析字符网格地图。
 *
 * <p>首行：{@code cols rows tileSize}；其后逐行字符：{@code .}=平地, {@code #}=山地, {@code ~}=水域。
 * 以 {@code #} 开头的行视为注释，空行被忽略。</p>
 */
public class MapLoader {

    public GameMap loadFile(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        int cols = -1, rows = -1, tileSize = 20;
        List<String> gridLines = new ArrayList<>();
        boolean headerParsed = false;

        for (String raw : lines) {
            String line = raw;
            if (line.isBlank()) {
                continue;
            }
            // 注释行：仅当不是后续地图数据时跳过（数据中 # 表示山地，故仅当首字符为 # 且整行像注释才跳过）
            if (!headerParsed) {
                String trimmed = line.trim();
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
            gridLines.add(line);
            if (gridLines.size() == rows) {
                break;
            }
        }

        if (!headerParsed) {
            throw new IOException("Map header (cols rows tileSize) not found: " + file);
        }
        if (gridLines.size() < rows) {
            throw new IOException("Map has fewer rows (" + gridLines.size() + ") than declared (" + rows + "): " + file);
        }

        Tile[][] tiles = new Tile[rows][cols];
        for (int r = 0; r < rows; r++) {
            String line = gridLines.get(r);
            for (int c = 0; c < cols; c++) {
                char ch = c < line.length() ? line.charAt(c) : '.';
                tiles[r][c] = new Tile(terrainOf(ch));
            }
        }
        return new GameMap(cols, rows, tileSize, tiles);
    }

    private static TerrainType terrainOf(char ch) {
        switch (ch) {
            case '#': return TerrainType.MOUNTAIN;
            case '~': return TerrainType.WATER;
            case '.':
            default:  return TerrainType.PLAIN;
        }
    }
}
