package com.gduws.model.ai;

import com.gduws.model.Unit;
import com.gduws.model.UnitRole;
import com.gduws.model.UnitState;
import com.gduws.model.World;

/** 群体 AI 调度：按角色分派到 {@link ScoutAI} / {@link StrikeAI} */
public final class AISystem {

    /** 打击单位持续无活动超过该 tick 数则转为侦察单位 */
    private static final int STRIKE_IDLE_TIMEOUT = 240;

    private final ScoutAI scout = new ScoutAI();
    private final StrikeAI strike = new StrikeAI();

    public void update(World w) {
        for (Unit u : w.units) {
            if (u.isDead()) continue;
            if (u.state == UnitState.DEAD) continue;
            if (u.role == UnitRole.STRIKE) {
                maybeConvertIdleStrike(u, w);
            }
            if (u.role == UnitRole.SCOUT) {
                scout.update(u, w);
            } else {
                strike.update(u, w);
            }
        }
    }

    /** 打击单位长时间“罚站”（IDLE）超时后转为侦察，主动去寻找敌人 */
    private void maybeConvertIdleStrike(Unit u, World w) {
        boolean active = u.state != UnitState.IDLE;
        if (active) {
            u.lastActiveTick = w.tickCount();
            return;
        }
        if (w.tickCount() - u.lastActiveTick >= STRIKE_IDLE_TIMEOUT) {
            u.role = UnitRole.SCOUT;
            u.state = UnitState.SCOUTING;
            u.lastActiveTick = w.tickCount();
        }
    }
}
