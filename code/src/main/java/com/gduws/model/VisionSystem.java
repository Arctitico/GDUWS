package com.gduws.model;

/**
 * 视野系统：每 tick 计算每个单位 {@code sightRange} 范围内的敌方单位，
 * 并将发现写入本阵营 {@link IntelBoard}。
 */
public final class VisionSystem {

    public void update(World w) {
        for (Unit observer : w.units) {
            if (observer.isDead()) continue;
            int sight = observer.def.sightRange;
            if (sight <= 0) continue;
            IntelBoard board = w.intelOf(observer.faction);
            double sr2 = (double) sight * sight;
            for (Unit other : w.units) {
                if (other.faction == observer.faction) continue;
                if (other.isDead()) continue;
                double dx = other.x - observer.x;
                double dy = other.y - observer.y;
                if (dx * dx + dy * dy <= sr2) {
                    board.report(other, other.x, other.y, w.tickCount());
                }
            }
        }
    }
}
