package com.gduws.model.ai;

import java.awt.Point;

import com.gduws.model.IntelBoard;
import com.gduws.model.Unit;
import com.gduws.model.UnitState;
import com.gduws.model.World;

/**
 * 侦察单位 FSM：默认 {@link UnitState#SCOUTING}，向最旧未探索的区域大胆前进，
 * 迎着敌人火力继续侦察；仅当周边敌人过多才避战重规划。
 * 若发现敌弱我强，则就近歼灭目标，打完继续侦察。
 */
public final class ScoutAI {

    /** 重新选路前先走一段，避免每 tick 重算 */
    private static final int REPLAN_INTERVAL = 30;
    /** 判定"敌人太多需避战"的半径（像素） */
    private static final double DANGER_RADIUS = 200;
    /** 该半径内敌人数量达到此值才避战重规划路径 */
    private static final int DANGER_ENEMY_COUNT = 5;
    /** 主动出击的评估半径（像素） */
    private static final double ENGAGE_RADIUS = 260;
    /** 己方兵力 ≥ 敌方 * 该比例时才主动出击 */
    private static final double ENGAGE_POWER_RATIO = 1.8;

    public void update(Unit u, World w) {
        IntelBoard intel = w.intelOf(u.faction);

        // 机会主义攻击：敌弱我强时就近歼灭，打完回到侦察
        if (canFight(u)) {
            Unit prey = pickWeakPrey(u, w, intel);
            if (prey != null) {
                engage(u, w, prey);
                return;
            }
        }

        scout(u, w, intel);
    }

    /** 大胆沿既定路径前进；到达目标或周边敌人过多（避战）时才重选路径 */
    private void scout(Unit u, World w, IntelBoard intel) {
        u.state = UnitState.SCOUTING;
        u.currentTarget = null;

        boolean needGoal = u.path == null || u.path.isEmpty();
        if (!needGoal) {
            boolean tooMany = countNearbyEnemies(u, w, DANGER_RADIUS) >= DANGER_ENEMY_COUNT;
            // 路径未走完且敌人不算多 -> 迎着火力继续前进，不重算
            if (!(tooMany && w.tickCount() % REPLAN_INTERVAL == 0)) {
                return;
            }
        }

        int sc = w.map.toCol(u.x);
        int sr = w.map.toRow(u.y);
        Point regionCell = w.exploration().pickGoal(u.faction, sc, sr, u.def.movementType);
        if (regionCell == null) return;

        // 避战寻路：威胁场让路径绕开已知敌人
        u.path = w.pathfinder().findPath(u.def.movementType, sc, sr,
            regionCell.x, regionCell.y, /*avoidEnemies=*/true, intel);
        if (u.path != null && !u.path.isEmpty()) {
            u.moveGoal = u.path.peekLast();
        } else {
            u.path = null;
            u.moveGoal = null;
        }
    }

    /** 接敌：未进射程则逼近，进射程则停下由 CombatSystem 开火 */
    private void engage(Unit u, World w, Unit target) {
        u.currentTarget = target;
        double dx = target.x - u.x;
        double dy = target.y - u.y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist <= u.def.attack.maxAttackRange) {
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
                u.path = w.pathfinder().findPath(u.def.movementType, sc, sr, gc, gr, false, null);
                if (u.path != null && !u.path.isEmpty()) {
                    u.moveGoal = u.path.peekLast();
                }
            }
        }
    }

    private boolean canFight(Unit u) {
        return u.def.attack != null && u.def.attack.canAttackAnything();
    }

    /** 评估周边敌我兵力，仅在敌弱我强时返回最近的可命中目标 */
    private Unit pickWeakPrey(Unit u, World w, IntelBoard intel) {
        double friendly = 0, enemy = 0;
        double r2 = ENGAGE_RADIUS * ENGAGE_RADIUS;
        for (Unit o : w.units) {
            if (o.isDead()) continue;
            double dx = o.x - u.x;
            double dy = o.y - u.y;
            if (dx * dx + dy * dy > r2) continue;
            if (o.faction == u.faction) friendly += o.hp;
            else enemy += o.hp;
        }
        if (enemy <= 0 || friendly < enemy * ENGAGE_POWER_RATIO) return null;

        Unit best = null;
        double bestD2 = Double.MAX_VALUE;
        for (IntelBoard.IntelEntry e : intel.knownEnemies()) {
            Unit t = e.enemy;
            if (t.isDead()) continue;
            if (!u.def.attack.canTarget(t)) continue;
            double dx = t.x - u.x;
            double dy = t.y - u.y;
            double d2 = dx * dx + dy * dy;
            if (d2 > r2) continue;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = t;
            }
        }
        return best;
    }

    private int countNearbyEnemies(Unit u, World w, double radius) {
        int n = 0;
        double r2 = radius * radius;
        for (Unit o : w.units) {
            if (o.isDead()) continue;
            if (o.faction == u.faction) continue;
            double dx = o.x - u.x;
            double dy = o.y - u.y;
            if (dx * dx + dy * dy <= r2) n++;
        }
        return n;
    }
}
