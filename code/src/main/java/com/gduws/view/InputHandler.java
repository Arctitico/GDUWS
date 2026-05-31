package com.gduws.view;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import com.gduws.control.DeployController;
import com.gduws.control.GameState;
import com.gduws.control.GameStateManager;
import com.gduws.model.Faction;
import com.gduws.model.Unit;
import com.gduws.model.World;

/**
 * 鼠标交互：
 * <ul>
 *   <li>DEPLOY：左键在空地放置，在己方单位上点击则切换其侦察/打击角色；右键移除。</li>
 *   <li>BATTLE：左键选中己方单位仅用于查看信息；右键取消选择。
 *       战斗中所有单位完全自主行动，玩家无法操控。</li>
 * </ul>
 */
public class InputHandler extends MouseAdapter {

    private final DeployController deploy;
    private final GameStateManager stateManager;
    private final Runnable onChange;
    private final GamePanel panel;

    public InputHandler(DeployController deploy, GameStateManager stateManager,
                        Runnable onChange, GamePanel panel) {
        this.deploy = deploy;
        this.stateManager = stateManager;
        this.onChange = onChange;
        this.panel = panel;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (stateManager.is(GameState.DEPLOY)) {
            handleDeploy(e);
        } else if (stateManager.is(GameState.BATTLE)) {
            handleBattle(e);
        }
        onChange.run();
    }

    private void handleDeploy(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            // 左键点在己方单位上则切换其角色，否则尝试放置
            if (!deploy.toggleRoleAt(e.getX(), e.getY())) {
                deploy.tryPlace(e.getX(), e.getY());
            }
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            deploy.tryRemove(e.getX(), e.getY());
        }
    }

    private void handleBattle(MouseEvent e) {
        // 战斗中单位完全自主，左键仅用于选中查看，右键取消
        GameRenderer r = panel.renderer();
        World w = panel.world();
        if (e.getButton() == MouseEvent.BUTTON3) {
            r.selectedUnit = null;
            return;
        }
        if (e.getButton() != MouseEvent.BUTTON1) return;

        Unit hit = w.unitAt(e.getX(), e.getY(), 0);
        r.selectedUnit = (hit != null && hit.faction == Faction.PLAYER) ? hit : null;
    }
}
