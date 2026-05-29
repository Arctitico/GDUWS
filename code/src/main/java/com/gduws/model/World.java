package com.gduws.model;

import java.util.ArrayList;
import java.util.List;

/** 战场世界：持有地图与全部单位。M1 阶段仅作容器。 */
public class World {

    public final GameMap map;
    public final List<Unit> units = new ArrayList<>();

    public World(GameMap map) {
        this.map = map;
    }

    public void addUnit(Unit u) {
        units.add(u);
    }

    public void removeUnit(Unit u) {
        units.remove(u);
    }

    /** 返回与给定圆（像素坐标 + 半径）发生重叠的第一个单位，无则 null。 */
    public Unit unitAt(double px, double py, double radius) {
        for (Unit u : units) {
            double dx = u.x - px;
            double dy = u.y - py;
            double rr = u.def.radius + radius;
            if (dx * dx + dy * dy <= rr * rr) {
                return u;
            }
        }
        return null;
    }

    public int countAlive(Faction f) {
        int n = 0;
        for (Unit u : units) {
            if (u.faction == f && !u.isDead()) {
                n++;
            }
        }
        return n;
    }
}
