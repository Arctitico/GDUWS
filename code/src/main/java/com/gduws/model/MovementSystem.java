package com.gduws.model;

import java.awt.Point;

/**
 * 移动系统：每 tick 沿 {@link Unit#path} 推进单位，并插值更新朝向。
 *
 * <p>到达路径队首格中心后将其出队，路径为空即停止。</p>
 */
public final class MovementSystem {

    /** 朝向每 tick 最大旋转弧度（约 11°）。 */
    private static final double MAX_TURN_PER_TICK = Math.PI / 16;

    public void update(World w) {
        for (Unit u : w.units) {
            if (u.isDead() || u.path == null || u.path.isEmpty()) {
                continue;
            }
            Point next = u.path.peekFirst();
            double tx = w.map.cellCenterX(next.x);
            double ty = w.map.cellCenterY(next.y);
            double dx = tx - u.x;
            double dy = ty - u.y;
            double dist = Math.sqrt(dx * dx + dy * dy);

            // 转向（朝目标方向逐步插值）
            if (dist > 1e-6) {
                double targetAngle = Math.atan2(dy, dx);
                u.facing = turnToward(u.facing, targetAngle, MAX_TURN_PER_TICK);
            }

            double step = u.def.moveSpeed;
            if (dist <= step) {
                // 到达本格中心，吸附并出队
                u.x = tx;
                u.y = ty;
                u.path.pollFirst();
                if (u.path.isEmpty()) {
                    u.moveGoal = null;
                }
            } else {
                u.x += dx / dist * step;
                u.y += dy / dist * step;
            }
        }
    }

    private static double turnToward(double from, double to, double maxStep) {
        double diff = normalize(to - from);
        if (Math.abs(diff) <= maxStep) {
            return normalize(to);
        }
        return normalize(from + Math.signum(diff) * maxStep);
    }

    private static double normalize(double a) {
        while (a > Math.PI)  a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }
}
