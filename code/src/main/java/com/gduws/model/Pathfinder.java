package com.gduws.model;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A* 寻路：8 邻接 + Octile 启发式。按单位 {@link MovementType} 取可通行格。
 * 当 {@code avoidEnemies=true} 时，g 值叠加敌情威胁场，使路径绕开已知敌人（侦察避战）。
 */
public final class Pathfinder {

    private static final double SQRT2 = Math.sqrt(2);

    /** 威胁场常数：距离已知敌人 D 内每格额外代价 = max(0, THREAT_K - D)。 */
    private static final double THREAT_RANGE = 6;   // 影响半径（格）
    private static final double THREAT_WEIGHT = 4;  // 每个敌人单位贡献的强度

    private final GameMap map;

    public Pathfinder(GameMap map) {
        this.map = map;
    }

    /** 像素坐标版本：自动换算到格。 */
    public Deque<Point> findPath(Unit u, double goalPxX, double goalPxY, boolean avoidEnemies,
                                 IntelBoard enemyIntel) {
        int sc = map.toCol(u.x);
        int sr = map.toRow(u.y);
        int gc = map.toCol(goalPxX);
        int gr = map.toRow(goalPxY);
        return findPath(u.def.movementType, sc, sr, gc, gr, avoidEnemies, enemyIntel);
    }

    /** 格坐标 A*。返回从起点下一步起、以终点结尾的格序列；不可达返回 null。 */
    public Deque<Point> findPath(MovementType mt, int sc, int sr, int gc, int gr,
                                 boolean avoidEnemies, IntelBoard enemyIntel) {
        if (!map.inBounds(gc, gr) || !map.isPassable(gc, gr, mt)) {
            return null;
        }
        if (sc == gc && sr == gr) {
            return new ArrayDeque<>();
        }

        Map<Long, Node> all = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>();

        Node start = new Node(sc, sr, 0, heuristic(sc, sr, gc, gr), null);
        all.put(key(sc, sr), start);
        open.add(start);

        int[][] dirs = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        while (!open.isEmpty()) {
            Node cur = open.poll();
            if (cur.closed) continue;
            cur.closed = true;
            if (cur.cx == gc && cur.cy == gr) {
                return reconstruct(cur);
            }
            for (int[] d : dirs) {
                int nx = cur.cx + d[0];
                int ny = cur.cy + d[1];
                if (!map.isPassable(nx, ny, mt)) continue;
                // 对角线不允许穿越两个不可通行的角
                if (d[0] != 0 && d[1] != 0) {
                    if (!map.isPassable(cur.cx + d[0], cur.cy, mt)) continue;
                    if (!map.isPassable(cur.cx, cur.cy + d[1], mt)) continue;
                }
                double step = (d[0] != 0 && d[1] != 0) ? SQRT2 : 1.0;
                double extra = avoidEnemies ? threatCost(nx, ny, enemyIntel) : 0.0;
                double ng = cur.g + step + extra;
                long k = key(nx, ny);
                Node nb = all.get(k);
                if (nb == null) {
                    nb = new Node(nx, ny, ng, heuristic(nx, ny, gc, gr), cur);
                    all.put(k, nb);
                    open.add(nb);
                } else if (ng < nb.g && !nb.closed) {
                    nb.g = ng;
                    nb.parent = cur;
                    nb.f = ng + nb.h;
                    open.add(nb); // 重新入堆，旧节点靠 closed 标记忽略
                }
            }
        }
        return null;
    }

    private double threatCost(int cx, int cy, IntelBoard intel) {
        if (intel == null) return 0;
        double sum = 0;
        double px = map.cellCenterX(cx);
        double py = map.cellCenterY(cy);
        for (IntelBoard.IntelEntry e : intel.knownEnemies()) {
            double dx = (e.x - px) / map.tileSize;
            double dy = (e.y - py) / map.tileSize;
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d < THREAT_RANGE) {
                sum += THREAT_WEIGHT * (THREAT_RANGE - d) / THREAT_RANGE;
            }
        }
        return sum;
    }

    private static double heuristic(int x, int y, int gx, int gy) {
        int dx = Math.abs(x - gx);
        int dy = Math.abs(y - gy);
        return (dx + dy) + (SQRT2 - 2) * Math.min(dx, dy); // Octile
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    private static Deque<Point> reconstruct(Node end) {
        ArrayDeque<Point> path = new ArrayDeque<>();
        Node n = end;
        while (n.parent != null) {
            path.addFirst(new Point(n.cx, n.cy));
            n = n.parent;
        }
        return path;
    }

    private static final class Node implements Comparable<Node> {
        final int cx, cy;
        double g, h, f;
        Node parent;
        boolean closed;

        Node(int cx, int cy, double g, double h, Node parent) {
            this.cx = cx;
            this.cy = cy;
            this.g = g;
            this.h = h;
            this.f = g + h;
            this.parent = parent;
        }

        @Override
        public int compareTo(Node o) {
            return Double.compare(this.f, o.f);
        }
    }
}
