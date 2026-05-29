package com.gduws.view;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import com.gduws.control.GameState;
import com.gduws.control.GameStateManager;

/** 处理布兵阶段鼠标交互：左键放置、右键移除。 */
public class InputHandler extends MouseAdapter {

    private final DeployController deploy;
    private final GameStateManager stateManager;
    private final Runnable onChange;

    public InputHandler(DeployController deploy, GameStateManager stateManager, Runnable onChange) {
        this.deploy = deploy;
        this.stateManager = stateManager;
        this.onChange = onChange;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (!stateManager.is(GameState.DEPLOY)) {
            return;
        }
        if (e.getButton() == MouseEvent.BUTTON1) {
            deploy.tryPlace(e.getX(), e.getY());
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            deploy.tryRemove(e.getX(), e.getY());
        }
        onChange.run();
    }
}
