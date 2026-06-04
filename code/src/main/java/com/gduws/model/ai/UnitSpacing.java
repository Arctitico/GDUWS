package com.gduws.model.ai;

import java.awt.Point;

import com.gduws.model.Unit;
import com.gduws.model.World;

/**
 * 单位空间协调工具：防止多单位挤在同一位置，为接近目标寻找不拥挤的落点
 * 所有方法均为纯函数，无实例状态
 */
public final class UnitSpacing {

    /** 友邻间距小于该像素视为挤在一起，需要错开 */
    public static final double MIN_SEPARATION = 16;
    /** 目标距离超过最大射程该比例时主动追击，把目标拉回有效射程内 */
    public static final double PURSUE_RATIO = 0.9;

    private UnitSpacing() { /* 工具类，禁止实例化 */ }

    /** 本单位是否与其他友方单位挤在一起（间距过近） */
    public static boolean isCrowded(Unit u, World w) {
        double min2 = MIN_SEPARATION * MIN_SEPARATION;
        for (Unit o : w.units) {
            if (o == u || o.isDead() || o.faction != u.faction) continue;
            double dx = o.x - u.x;
            double dy = o.y - u.y;
            if (dx * dx + dy * dy < min2) return true;
        }
        return false;
    }

    /** 指定格是否已被其他友方单位占据（当前所在格）或预订（移动终点） */
    public static boolean tileTakenByFriendly(Unit self, World w, int col, int row) {
        for (Unit o : w.units) {
            if (o == self || o.isDead() || o.faction != self.faction) continue;
            if (w.map.toCol(o.x) == col && w.map.toRow(o.y) == row) return true;
            if (o.moveGoal != null && o.moveGoal.x == col && o.moveGoal.y == row) return true;
        }
        return false;
    }

    /**
     * 为本单位寻找一个可通行的接近落点：在目标周围由近及远搜索，
     * 返回首个本单位移动域可通行、且落点中心位于给定射程内的格。
     * 已排除被友方占据或预订的格，避免扎堆。
     */
    public static Point findApproachTile(Unit u, World w, Unit target, double range) {
        int tc = w.map.toCol(target.x);
        int tr = w.map.toRow(target.y);
        int maxR = (int) Math.ceil(range / w.map.tileSize) + 2;
        Point best = null;
        double bestD2 = Double.MAX_VALUE;
        for (int r = 1; r <= maxR; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = tc + dx;
                    int ny = tr + dy;
                    if (!w.map.isPassable(nx, ny, u.def.movementType)) continue;
                    double cx = w.map.cellCenterX(nx);
                    double cy = w.map.cellCenterY(ny);
                    double ex = cx - target.x;
                    double ey = cy - target.y;
                    if (ex * ex + ey * ey > range * range) continue;
                    if (tileTakenByFriendly(u, w, nx, ny)) continue;
                    double ux = cx - u.x;
                    double uy = cy - u.y;
                    double d2 = ux * ux + uy * uy;
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        best = new Point(nx, ny);
                    }
                }
            }
            if (best != null) break;
        }
        return best;
    }

    /** 在本单位附近寻找一个未被友邻占据的可通行格，用于错开拥挤 */
    public static Point findFreeNearbyTile(Unit u, World w) {
        int sc = w.map.toCol(u.x);
        int sr = w.map.toRow(u.y);
        int searchRadius = 5;
        for (int r = 1; r <= searchRadius; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = sc + dx;
                    int ny = sr + dy;
                    if (!w.map.isPassable(nx, ny, u.def.movementType)) continue;
                    if (tileTakenByFriendly(u, w, nx, ny)) continue;
                    return new Point(nx, ny);
                }
            }
        }
        return null;
    }
}
