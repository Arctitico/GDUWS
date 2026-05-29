package com.gduws.control;

import javax.swing.Timer;

import com.gduws.model.Faction;
import com.gduws.model.World;

/**
 * 主循环：固定步长 30 tick/s 推进模拟。仅在 {@link GameState#BATTLE} 状态下推进。
 *
 * <p>用 Swing {@link Timer} 实现，回调在 EDT 上执行，省去与渲染线程的同步成本。</p>
 */
public final class GameLoop {

    public static final int TICKS_PER_SECOND = 30;
    private static final int STEP_MS = 1000 / TICKS_PER_SECOND;

    private final World world;
    private final GameStateManager stateManager;
    private final Runnable afterTick;
    private final Timer timer;
    private java.util.function.Consumer<Faction> onVictory;

    public GameLoop(World world, GameStateManager stateManager, Runnable afterTick) {
        this.world = world;
        this.stateManager = stateManager;
        this.afterTick = afterTick;
        this.timer = new Timer(STEP_MS, e -> step());
        this.timer.setCoalesce(true);
    }

    public void setOnVictory(java.util.function.Consumer<Faction> onVictory) {
        this.onVictory = onVictory;
    }

    public void start() {
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    public void stop() {
        timer.stop();
    }

    private void step() {
        if (stateManager.is(GameState.BATTLE)) {
            world.tick();
            Faction w = world.winner();
            if (w != null) {
                stateManager.setState(GameState.RESULT);
                timer.stop();
                if (onVictory != null) onVictory.accept(w);
            }
        }
        if (afterTick != null) {
            afterTick.run();
        }
    }
}
