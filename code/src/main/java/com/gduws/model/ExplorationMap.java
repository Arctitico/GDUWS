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

    /** 为侦察单位挑选下一个探索目标格（兼顾"久未访问"与"路远"，但抑制原路折返） */
    public Point pickGoal(Faction faction, int fromCx, int fromCy, MovementType mt) {
        return pickGoal(faction, fromCx, fromCy, mt, 0, 0);
    }

    /**
     * 为侦察单位挑选下一个探索目标格。
     * 保持"选远处、奔远处"的侦察风格：偏好久未访问且距离较远的区块；
     * 但对与当前朝向相反（即沿来路折返）的方向施加惩罚，避免在对角线上来回往返，
     * 从而把侦察覆盖范围扩展到对角线以外的区域。
     *
     * @param headingCx 当前朝向向量的列分量（来路→去路，0 表示无朝向）
     * @param headingCy 当前朝向向量的行分量
     */
    public Point pickGoal(Faction faction, int fromCx, int fromCy, MovementType mt,
                          double headingCx, double headingCy) {
        int[][] grid = visit.get(faction);
        int fromRc = fromCx / REGION_SIZE;
        int fromRr = fromCy / REGION_SIZE;

        // 关键：先按单位通行域做连通性洪泛，得到"从当前位置真正走得到"的格集合
        // 否则可能给潜艇/舰船等水域单位分配到不连通水域的目标，导致 A* 失败、原地不动
        boolean[][] reachable = computeReachable(fromCx, fromCy, mt);

        // 朝向单位向量（若无朝向则为 0）
        double hLen = Math.sqrt(headingCx * headingCx + headingCy * headingCy);
        double hx = hLen > 1e-6 ? headingCx / hLen : 0;
        double hy = hLen > 1e-6 ? headingCy / hLen : 0;

        // 只在"可达"区块中找最旧访问 tick，作为"久未探索"的基准
        int oldestTick = Integer.MAX_VALUE;
        for (int rr = 0; rr < regionRows; rr++) {
            for (int rc = 0; rc < regionCols; rc++) {
                if (rc == fromRc && rr == fromRr) continue;
                if (reachableCellIn(rc, rr, reachable) == null) continue;
                int t = grid[rr][rc];
                if (t < oldestTick) oldestTick = t;
            }
        }
        if (oldestTick == Integer.MAX_VALUE) return null;

        // 地图对角线长度，用于把距离归一化到 0..1
        double diag = Math.sqrt(regionCols * (double) regionCols
                + regionRows * (double) regionRows);

        int bestRc = -1, bestRr = -1;
        Point bestCell = null;
        double bestScore = -Double.MAX_VALUE;

        for (int rr = 0; rr < regionRows; rr++) {
            for (int rc = 0; rc < regionCols; rc++) {
                if (rc == fromRc && rr == fromRr) continue;
                // 跳过该单位通行域走不到的区块
                Point cell = reachableCellIn(rc, rr, reachable);
                if (cell == null) continue;

                int t = grid[rr][rc];
                int drc = rc - fromRc;
                int drr = rr - fromRr;
                double dist = Math.sqrt(drc * drc + drr * drr);
                double distNorm = diag > 1e-6 ? dist / diag : 0;

                // 久未探索优先：在最旧基准之外，越新越扣分（保证优先覆盖未探索区）
                double staleness = (t <= oldestTick) ? 1.0 : 0.0;

                // 朝向一致性：候选方向与当前朝向的夹角余弦（-1 折返，+1 同向）
                double align = 0;
                if (hLen > 1e-6 && dist > 1e-6) {
                    align = (drc * hx + drr * hy) / dist;
                }
                // 折返惩罚：仅惩罚明显反向（cos<0），同向/侧向不奖励也不惩罚
                double reversePenalty = align < 0 ? -align : 0;

                // 综合评分：久未探索为主，路远加分，折返大幅扣分
                double score = staleness * 10.0      // 优先未探索区
                        + distNorm * 3.0             // 保持"奔远处"风格
                        - reversePenalty * 4.0;      // 抑制对角线折返

                if (score > bestScore) {
                    bestScore = score;
                    bestRc = rc;
                    bestRr = rr;
                    bestCell = cell;
                }
            }
        }
        if (bestRc < 0) return null;
        // 返回区块内一处"可达"的格，保证后续 A* 必能找到路径
        return bestCell;
    }

    /** 在区块 (rc,rr) 内找一处可达格（先取靠中心者）；无可达格返回 null */
    private Point reachableCellIn(int rc, int rr, boolean[][] reachable) {
        int baseC = rc * REGION_SIZE;
        int baseR = rr * REGION_SIZE;
        int midC = Math.min(baseC + REGION_SIZE / 2, map.cols - 1);
        int midR = Math.min(baseR + REGION_SIZE / 2, map.rows - 1);
        if (reachable[midR][midC]) return new Point(midC, midR);
        for (int dr = 0; dr < REGION_SIZE; dr++) {
            int ry = baseR + dr;
            if (ry >= map.rows) break;
            for (int dc = 0; dc < REGION_SIZE; dc++) {
                int cx = baseC + dc;
                if (cx >= map.cols) break;
                if (reachable[ry][cx]) return new Point(cx, ry);
            }
        }
        return null;
    }

    /**
     * 从 (fromCx,fromCy) 起按 {@code mt} 通行域做 8 邻接洪泛，
     * 返回与起点连通（即真正可达）的格集合。对角线遵循与寻路一致的"不穿角"规则。
     */
    private boolean[][] computeReachable(int fromCx, int fromCy, MovementType mt) {
        boolean[][] visited = new boolean[map.rows][map.cols];
        if (!map.inBounds(fromCx, fromCy) || !map.isPassable(fromCx, fromCy, mt)) {
            // 起点本身不可通行（理论上不应发生）：退化为"全可达"，避免完全冻结
            for (boolean[] row : visited) Arrays.fill(row, true);
            return visited;
        }
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        visited[fromCy][fromCx] = true;
        q.add(new int[] {fromCx, fromCy});
        int[][] dirs = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        while (!q.isEmpty()) {
            int[] c = q.poll();
            int cx = c[0], cy = c[1];
            for (int[] d : dirs) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                if (!map.isPassable(nx, ny, mt)) continue;
                if (visited[ny][nx]) continue;
                // 对角线不允许穿越两个不可通行的角，与 Pathfinder 保持一致
                if (d[0] != 0 && d[1] != 0) {
                    if (!map.isPassable(cx + d[0], cy, mt)) continue;
                    if (!map.isPassable(cx, cy + d[1], mt)) continue;
                }
                visited[ny][nx] = true;
                q.add(new int[] {nx, ny});
            }
        }
        return visited;
    }
}
