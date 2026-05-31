package com.gduws.model.ai;

import java.awt.Point;

import com.gduws.model.IntelBoard;
import com.gduws.model.Unit;
import com.gduws.model.UnitState;
import com.gduws.model.World;

/**
 * 打击单位 FSM：IDLE → MOVING_TO_TARGET → ATTACKING，
 * 周边兵力悬殊时转 RETREATING
 */
public final class StrikeAI {

    /** 兵力评估半径（像素） */
    private static final double THREAT_RADIUS = 220;
    /** 友方兵力 < 敌方 * 该比例时撤退 */
    private static final double RETREAT_RATIO = 0.5;
    /** 撤退距离（像素） */
    private static final double RETREAT_DISTANCE = 260;
    /** 重新选路间隔 */
    private static final int REPLAN_INTERVAL = 20;
    /** 友邻间距小于该像素视为挤在一起，需要错开 */
    private static final double MIN_SEPARATION = 16;

    public void update(Unit u, World w) {
        IntelBoard intel = w.intelOf(u.faction);

        // 处理撤退中
        if (u.state == UnitState.RETREATING) {
            if (u.path == null || u.path.isEmpty()) {
                // 撤至安全点后回 IDLE，重新评估
                u.state = UnitState.IDLE;
            } else {
                return;
            }
        }

        // 兵力悬殊则撤退
        if (intel.hasAnyEnemy() && shouldRetreat(u, w)) {
            startRetreat(u, w);
            return;
        }

        if (!intel.hasAnyEnemy()) {
            u.state = UnitState.IDLE;
            u.path = null;
            u.moveGoal = null;
            u.currentTarget = null;
            return;
        }

        Unit target = selectNearestAttackable(u, w);
        if (target == null) {
            u.state = UnitState.IDLE;
            u.path = null;
            u.currentTarget = null;
            return;
        }
        u.currentTarget = target;

        double dx = target.x - u.x;
        double dy = target.y - u.y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist <= u.def.attack.maxAttackRange) {
            // 正在错位移动则继续走完，避免抖动
            if (u.path != null && !u.path.isEmpty()) {
                u.state = UnitState.MOVING_TO_TARGET;
                return;
            }
            // 在射程内但与友邻挤在一起：错开到附近空位，否则原地开火
            if (isCrowded(u, w)) {
                Point spot = findApproachTile(u, w, target);
                if (spot != null
                        && !(w.map.toCol(u.x) == spot.x && w.map.toRow(u.y) == spot.y)) {
                    int sc = w.map.toCol(u.x);
                    int sr = w.map.toRow(u.y);
                    u.path = w.pathfinder().findPath(
                        u.def.movementType, sc, sr, spot.x, spot.y, false, null);
                    if (u.path != null && !u.path.isEmpty()) {
                        u.moveGoal = u.path.peekLast();
                        u.state = UnitState.MOVING_TO_TARGET;
                        return;
                    }
                }
            }
            // 在射程内：停下，由 CombatSystem 开火
            u.state = UnitState.ATTACKING;
            u.path = null;
            u.moveGoal = null;
        } else {
            u.state = UnitState.MOVING_TO_TARGET;
            boolean needPath = u.path == null || u.path.isEmpty()
                || (w.tickCount() % REPLAN_INTERVAL == 0);
            if (needPath) {
                int sc = w.map.toCol(u.x);
                int sr = w.map.toRow(u.y);
                int gc = w.map.toCol(target.x);
                int gr = w.map.toRow(target.y);
                // 走向目标射程内一处未被友邻占用的落点，避免多单位挤向同一格；
                // 找不到则退回目标格附近
                Point approach = findApproachTile(u, w, target);
                if (approach != null) {
                    gc = approach.x;
                    gr = approach.y;
                }
                u.path = w.pathfinder().findPath(u.def.movementType, sc, sr, gc, gr, false, null);
                if (u.path != null && !u.path.isEmpty()) {
                    u.moveGoal = u.path.peekLast();
                }
            }
        }
    }

    /**
     * 为本单位寻找一个可通行的接近落点：在目标周围由近及远搜索，
     * 返回首个本单位移动域可通行、且落点中心位于本单位射程内的格
     */
    private Point findApproachTile(Unit u, World w, Unit target) {
        int tc = w.map.toCol(target.x);
        int tr = w.map.toRow(target.y);
        double range = u.def.attack.maxAttackRange;
        // 以射程换算的格数为半径上限，外加余量
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
                    // 已被其他友方单位占据或预订的落点跳过，避免扎堆
                    if (tileTakenByFriendly(u, w, nx, ny)) continue;
                    // 选离本单位最近的可行落点，减少绕路
                    double ux = cx - u.x;
                    double uy = cy - u.y;
                    double d2 = ux * ux + uy * uy;
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        best = new Point(nx, ny);
                    }
                }
            }
            // 当前环已找到射程内落点即可停止外扩
            if (best != null) break;
        }
        return best;
    }

    /** 本单位是否与其他友方单位挤在一起（间距过近） */
    private boolean isCrowded(Unit u, World w) {
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
    private boolean tileTakenByFriendly(Unit self, World w, int col, int row) {
        for (Unit o : w.units) {
            if (o == self || o.isDead() || o.faction != self.faction) continue;
            if (w.map.toCol(o.x) == col && w.map.toRow(o.y) == row) return true;
            if (o.moveGoal != null && o.moveGoal.x == col && o.moveGoal.y == row) return true;
        }
        return false;
    }

    /** 在已知敌情中选最近、且本单位攻击域可命中的敌人 */
    private Unit selectNearestAttackable(Unit u, World w) {
        Unit best = null;
        double bestD2 = Double.MAX_VALUE;
        for (IntelBoard.IntelEntry e : w.intelOf(u.faction).knownEnemies()) {
            Unit t = e.enemy;
            if (t.isDead()) continue;
            if (!u.def.attack.canTarget(t)) continue;
            double dx = t.x - u.x;
            double dy = t.y - u.y;
            double d2 = dx * dx + dy * dy;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = t;
            }
        }
        return best;
    }

    private boolean shouldRetreat(Unit u, World w) {
        double friendly = 0, enemy = 0;
        double r2 = THREAT_RADIUS * THREAT_RADIUS;
        for (Unit o : w.units) {
            if (o.isDead()) continue;
            double dx = o.x - u.x;
            double dy = o.y - u.y;
            if (dx * dx + dy * dy > r2) continue;
            double power = o.hp;
            if (o.faction == u.faction) friendly += power;
            else enemy += power;
        }
        return enemy > 0 && friendly < enemy * RETREAT_RATIO;
    }

    private void startRetreat(Unit u, World w) {
        // 已知敌人质心
        double ex = 0, ey = 0;
        int n = 0;
        for (IntelBoard.IntelEntry e : w.intelOf(u.faction).knownEnemies()) {
            ex += e.x;
            ey += e.y;
            n++;
        }
        if (n == 0) return;
        ex /= n;
        ey /= n;
        double dx = u.x - ex;
        double dy = u.y - ey;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6) { dx = 1; dy = 0; len = 1; }
        double gx = u.x + dx / len * RETREAT_DISTANCE;
        double gy = u.y + dy / len * RETREAT_DISTANCE;

        // 夹到地图内
        int gc = clamp(w.map.toCol(gx), 0, w.map.cols - 1);
        int gr = clamp(w.map.toRow(gy), 0, w.map.rows - 1);

        // 寻找最近可通行的撤退落点
        Point goal = w.map.findNearestPassable(gc, gr, u.def.movementType, 6);
        if (goal == null) return;

        int sc = w.map.toCol(u.x);
        int sr = w.map.toRow(u.y);
        u.path = w.pathfinder().findPath(u.def.movementType, sc, sr,
            goal.x, goal.y, /*avoidEnemies=*/true, w.intelOf(u.faction));
        if (u.path != null && !u.path.isEmpty()) {
            u.state = UnitState.RETREATING;
            u.moveGoal = u.path.peekLast();
            u.currentTarget = null;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
