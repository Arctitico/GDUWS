package com.gduws.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 战场世界：持有地图、全部单位、各阵营情报，并按 tick 推进所有系统。 */
public class World {

    public final GameMap map;
    public final List<Unit> units = new ArrayList<>();

    private final Map<Faction, IntelBoard> intel = new EnumMap<>(Faction.class);
    private final VisionSystem visionSystem = new VisionSystem();
    private final MovementSystem movementSystem = new MovementSystem();
    private final Pathfinder pathfinder;
    private int tick = 0;

    public World(GameMap map) {
        this.map = map;
        this.pathfinder = new Pathfinder(map);
        for (Faction f : Faction.values()) {
            intel.put(f, new IntelBoard());
        }
    }

    public void addUnit(Unit u) {
        units.add(u);
    }

    public void removeUnit(Unit u) {
        units.remove(u);
        for (IntelBoard b : intel.values()) {
            b.forget(u);
        }
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

    public IntelBoard intelOf(Faction f) {
        return intel.get(f);
    }

    public Pathfinder pathfinder() {
        return pathfinder;
    }

    public int tickCount() {
        return tick;
    }

    /** 推进一逻辑帧：视野 → 移动（后续步将插入 AI / 战斗 / 胜负）。 */
    public void tick() {
        tick++;
        visionSystem.update(this);
        // AI 系统将在 Step 6 接入
        movementSystem.update(this);
        // 战斗与胜负判定将在 Step 7 接入
    }
}
