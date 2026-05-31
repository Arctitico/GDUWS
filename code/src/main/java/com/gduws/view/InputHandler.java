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
        // 左键即时处理；右键延后到释放时判断，避免与拖动平移冲突
        if (e.getButton() == MouseEvent.BUTTON1) {
            if (stateManager.is(GameState.DEPLOY)) {
                handleDeployLeft(e);
            } else if (stateManager.is(GameState.BATTLE)) {
                handleBattleLeft(e);
            }
            onChange.run();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON3) {
            return;
        }
        // 若本次右键是拖动地图则不触发移除/取消
        if (panel.rightDragMoved()) {
            return;
        }
        int wx = (int) panel.worldX(e.getX());
        int wy = (int) panel.worldY(e.getY());
        if (stateManager.is(GameState.DEPLOY)) {
            deploy.tryRemove(wx, wy);
        } else if (stateManager.is(GameState.BATTLE)) {
            panel.renderer().selectedUnit = null;
        }
        onChange.run();
    }

    private void handleDeployLeft(MouseEvent e) {
        int wx = (int) panel.worldX(e.getX());
        int wy = (int) panel.worldY(e.getY());
        // 左键点在己方单位上则切换其角色，否则尝试放置
        if (!deploy.toggleRoleAt(wx, wy)) {
            deploy.tryPlace(wx, wy);
        }
    }

    private void handleBattleLeft(MouseEvent e) {
        // 战斗中单位完全自主，左键仅用于选中查看
        GameRenderer r = panel.renderer();
        World w = panel.world();
        double wx = panel.worldX(e.getX());
        double wy = panel.worldY(e.getY());
        Unit hit = w.unitAt(wx, wy, 0);
        r.selectedUnit = (hit != null && hit.faction == Faction.PLAYER) ? hit : null;
    }
}
