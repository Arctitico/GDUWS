package com.gduws.view;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Deque;

import com.gduws.control.GameState;
import com.gduws.control.GameStateManager;
import com.gduws.model.Faction;
import com.gduws.model.Unit;
import com.gduws.model.World;

/**
 * 鼠标交互：
 * <ul>
 *   <li>DEPLOY：左键放置 / 右键移除（沿用 M1）。</li>
 *   <li>BATTLE：左键在己方单位上选中；选中后再次左键 = 指派移动目标。
 *       右键取消选择。供 Step 4/5 演示移动 + 视野/情报。</li>
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
            deploy.tryPlace(e.getX(), e.getY());
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            deploy.tryRemove(e.getX(), e.getY());
        }
    }

    private void handleBattle(MouseEvent e) {
        GameRenderer r = panel.renderer();
        World w = panel.world();
        if (e.getButton() == MouseEvent.BUTTON3) {
            r.selectedUnit = null;
            return;
        }
        if (e.getButton() != MouseEvent.BUTTON1) return;

        Unit hit = w.unitAt(e.getX(), e.getY(), 0);
        if (hit != null && hit.faction == Faction.PLAYER) {
            r.selectedUnit = hit;
            return;
        }
        Unit sel = r.selectedUnit;
        if (sel != null && !sel.isDead()) {
            Deque<java.awt.Point> path = w.pathfinder().findPath(
                sel, e.getX(), e.getY(), /*avoidEnemies=*/false, null);
            if (path != null) {
                sel.path = path;
                sel.moveGoal = path.isEmpty() ? null : path.peekLast();
            }
        }
    }
}
