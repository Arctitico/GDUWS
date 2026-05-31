package com.gduws.model;

import java.awt.Point;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * 侦察探索图：把地图划分为粗粒度区块，按阵营记录每块"最近访问 tick"，
 * 为侦察 AI 提供下一个最久未探索的目标格。与核心战场状态解耦，专司探索调度
 */
public final class ExplorationMap {

    /** 区块边长（格） */
    public static final int REGION_SIZE = 4;

    private final GameMap map;
    public final int regionCols;
    public final int regionRows;
    private final Map<Faction, int[][]> visit = new EnumMap<>(Faction.class);

    public ExplorationMap(GameMap map) {
        this.map = map;
        this.regionCols = (map.cols + REGION_SIZE - 1) / REGION_SIZE;
        this.regionRows = (map.rows + REGION_SIZE - 1) / REGION_SIZE;
        for (Faction f : Faction.values()) {
            visit.put(f, new int[regionRows][regionCols]);
        }
    }

    /** 清空全部访问记录（重玩时调用） */
    public void clear() {
        for (int[][] grid : visit.values()) {
            for (int[] row : grid) {
                Arrays.fill(row, 0);
            }
        }
    }

    /** 把像素坐标所处区块标记为在 tick 时刻被该阵营访问过 */
    public void markVisited(Faction faction, double px, double py, int tick) {
        int rc = map.toCol(px) / REGION_SIZE;
        int rr = map.toRow(py) / REGION_SIZE;
        if (rc >= 0 && rr >= 0 && rc < regionCols && rr < regionRows) {
            visit.get(faction)[rr][rc] = tick;
        }
    }

    /** 为侦察单位挑选下一个探索目标格（最久未访问区块中心附近的一格可通行格） */
    public Point pickGoal(Faction faction, int fromCx, int fromCy, MovementType mt) {
        int[][] grid = visit.get(faction);
        int fromRc = fromCx / REGION_SIZE;
        int fromRr = fromCy / REGION_SIZE;

        int bestRc = -1, bestRr = -1;
        int oldestTick = Integer.MAX_VALUE;
        double bestScore = -1;

        for (int rr = 0; rr < regionRows; rr++) {
            for (int rc = 0; rc < regionCols; rc++) {
                if (rc == fromRc && rr == fromRr) continue;
                int t = grid[rr][rc];
                int drc = rc - fromRc;
                int drr = rr - fromRr;
                double dist = Math.sqrt(drc * drc + drr * drr);
                // 主排序：最旧；同 tick 时倾向较远区域以分散探索
                if (t < oldestTick || (t == oldestTick && dist > bestScore)) {
                    oldestTick = t;
                    bestScore = dist;
                    bestRc = rc;
                    bestRr = rr;
                }
            }
        }
        if (bestRc < 0) return null;

        // 在所选区域内寻找一格可通行格
        int baseC = bestRc * REGION_SIZE;
        int baseR = bestRr * REGION_SIZE;
        int cc = Math.min(baseC + REGION_SIZE / 2, map.cols - 1);
        int cr = Math.min(baseR + REGION_SIZE / 2, map.rows - 1);
        return map.findNearestPassable(cc, cr, mt, REGION_SIZE);
    }
}
