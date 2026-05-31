package com.gduws.model.ai;

import com.gduws.model.Unit;
import com.gduws.model.UnitRole;
import com.gduws.model.UnitState;
import com.gduws.model.World;

/** 群体 AI 调度：按角色分派到 {@link ScoutAI} / {@link StrikeAI} */
public final class AISystem {

    private final ScoutAI scout = new ScoutAI();
    private final StrikeAI strike = new StrikeAI();

    public void update(World w) {
        for (Unit u : w.units) {
            if (u.isDead()) continue;
            if (u.state == UnitState.DEAD) continue;
            if (u.role == UnitRole.SCOUT) {
                scout.update(u, w);
            } else {
                strike.update(u, w);
            }
        }
    }
}
