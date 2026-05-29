package com.gduws.control;

/** 管理游戏总流程状态切换：选关 → 布兵 → 战斗 → 结算。 */
public class GameStateManager {

    private GameState state = GameState.DEPLOY; // M1 阶段直接从布兵开始

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public boolean is(GameState s) {
        return state == s;
    }
}
