package com.gduws.model.ai;

import java.awt.Point;

import com.gduws.model.IntelBoard;
import com.gduws.model.Unit;
import com.gduws.model.UnitState;
import com.gduws.model.World;

/**
 * 侦察单位 FSM：全程 {@link UnitState#SCOUTING}，向最旧未探索的区域前进，
 * 寻路启用威胁场以远离已知敌人
 */
public final class ScoutAI {

    /** 重新选路前先走一段，避免每 tick 重算 */
    private static final int REPLAN_INTERVAL = 30;

    public void update(Unit u, World w) {
        u.state = UnitState.SCOUTING;
        IntelBoard ownIntel = w.intelOf(u.faction);
        IntelBoard enemyIntel = w.intelOf(u.faction); // 自己看到的敌人就是要避开的

        boolean needGoal = u.path == null || u.path.isEmpty();
        if (!needGoal && (w.tickCount() % REPLAN_INTERVAL == 0) && ownIntel.hasAnyEnemy()) {
            // 周期性根据最新敌情重算路径
            needGoal = true;
        }
        if (!needGoal) return;

        int sc = w.map.toCol(u.x);
        int sr = w.map.toRow(u.y);
        Point regionCell = w.pickScoutGoal(u.faction, sc, sr, u.def.movementType);
        if (regionCell == null) return;

        u.path = w.pathfinder().findPath(u.def.movementType, sc, sr,
            regionCell.x, regionCell.y, /*avoidEnemies=*/true, enemyIntel);
        if (u.path != null && !u.path.isEmpty()) {
            u.moveGoal = u.path.peekLast();
        } else {
            u.path = null;
            u.moveGoal = null;
        }
    }
}
