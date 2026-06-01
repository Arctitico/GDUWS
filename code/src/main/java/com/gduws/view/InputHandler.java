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
 *   <li>BATTLE：左键单击选中单个己方单位，或左键按住拖动框选多个己方单位（松开生效）；
 *       右键取消选择。被选中的单位才显示攻击范围与寻路。
 *       战斗中所有单位完全自主行动，玩家无法操控。</li>
 * </ul>
 */
public class InputHandler extends MouseAdapter {

    /** 判定为框选（而非单击）的最小拖动像素阈值 */
    private static final int DRAG_THRESHOLD = 4;

    private final DeployController deploy;
    private final GameStateManager stateManager;
    private final Runnable onChange;
    private final GamePanel panel;

    /** 战斗中左键是否处于按下状态，以及按下时的屏幕坐标 */
    private boolean leftDown = false;
    private int pressX;
    private int pressY;

    public InputHandler(DeployController deploy, GameStateManager stateManager,
                        Runnable onChange, GamePanel panel) {
        this.deploy = deploy;
        this.stateManager = stateManager;
        this.onChange = onChange;
        this.panel = panel;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }
        if (stateManager.is(GameState.DEPLOY)) {
            handleDeployLeft(e);
            onChange.run();
        } else if (stateManager.is(GameState.BATTLE)) {
            // 战斗中左键按下先记录起点，松开时再判定单击或框选
            leftDown = true;
            pressX = e.getX();
            pressY = e.getY();
            panel.beginSelection(e.getX(), e.getY());
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (leftDown && stateManager.is(GameState.BATTLE)) {
            panel.updateSelection(e.getX(), e.getY());
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            if (leftDown && stateManager.is(GameState.BATTLE)) {
                finishBattleLeft(e);
            }
            leftDown = false;
            return;
        }
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
            panel.renderer().selectedUnits.clear();
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

    /** 松开左键：拖动距离小则视为单击选中，否则视为框选 */
    private void finishBattleLeft(MouseEvent e) {
        GameRenderer r = panel.renderer();
        World w = panel.world();
        int dx = Math.abs(e.getX() - pressX);
        int dy = Math.abs(e.getY() - pressY);
        r.selectedUnits.clear();
        if (dx <= DRAG_THRESHOLD && dy <= DRAG_THRESHOLD) {
            // 单击：选中光标下的己方单位
            Unit hit = w.unitAt(panel.worldX(e.getX()), panel.worldY(e.getY()), 0);
            if (hit != null && hit.faction == Faction.PLAYER) {
                r.selectedUnits.add(hit);
            }
        } else {
            // 框选：选中矩形范围内的全部己方单位（按世界坐标判定）
            double x0 = panel.worldX(pressX);
            double y0 = panel.worldY(pressY);
            double x1 = panel.worldX(e.getX());
            double y1 = panel.worldY(e.getY());
            double minX = Math.min(x0, x1);
            double maxX = Math.max(x0, x1);
            double minY = Math.min(y0, y1);
            double maxY = Math.max(y0, y1);
            for (Unit u : w.units) {
                if (u.faction != Faction.PLAYER) continue;
                if (u.x >= minX && u.x <= maxX && u.y >= minY && u.y <= maxY) {
                    r.selectedUnits.add(u);
                }
            }
        }
        panel.endSelection();
        onChange.run();
    }
}
