package com.gduws.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.gduws.model.ai.AISystem;

/** 战场世界：持有地图、全部单位、各阵营情报，并按 tick 推进所有系统 */
public class World {

    public final GameMap map;
    public final List<Unit> units = new ArrayList<>();

    private final Map<Faction, IntelBoard> intel = new EnumMap<>(Faction.class);
    private final VisionSystem visionSystem = new VisionSystem();
    private final MovementSystem movementSystem = new MovementSystem();
    private final AISystem aiSystem = new AISystem();
    private final CombatSystem combatSystem = new CombatSystem();
    private final Pathfinder pathfinder;
    private final ExplorationMap exploration;
    private int tick = 0;

    // 胜负判定
    private final Map<Faction, Integer> initialCount = new EnumMap<>(Faction.class);
    private Faction winner;
    private boolean battleStarted;

    public World(GameMap map) {
        this.map = map;
        this.pathfinder = new Pathfinder(map);
        this.exploration = new ExplorationMap(map);
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

    /** 返回与给定圆（像素坐标 + 半径）发生重叠的第一个单位，无则 null */
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

    public ExplorationMap exploration() {
        return exploration;
    }

    public CombatSystem combatSystem() {
        return combatSystem;
    }

    public int tickCount() {
        return tick;
    }

    public Faction winner() {
        return winner;
    }

    public int initialCountOf(Faction f) {
        Integer v = initialCount.get(f);
        return v == null ? 0 : v;
    }

    /** 战斗开始前调用：记录各阵营初始单位数，作为 90% 损失判定基线 */
    public void startBattle() {
        initialCount.clear();
        for (Faction f : Faction.values()) {
            initialCount.put(f, countAlive(f));
        }
        for (Unit u : units) {
            u.lastActiveTick = tick;
        }
        winner = null;
        battleStarted = true;
    }

    /** 重置到布兵前状态：清空所有单位、情报、探索记录、tick */
    public void reset() {
        units.clear();
        for (Faction f : Faction.values()) {
            intel.get(f).clearAll();
        }
        exploration.clear();
        combatSystem.recentShots.clear();
        initialCount.clear();
        winner = null;
        battleStarted = false;
        tick = 0;
    }

    /** 推进一逻辑帧：视野 → AI → 移动 → 战斗 → 清理 → 胜负 */
    public void tick() {
        if (winner != null) return;
        tick++;
        visionSystem.update(this);
        markFriendlyRegions();
        aiSystem.update(this);
        movementSystem.update(this);
        combatSystem.update(this);
        removeDead();
        if (battleStarted) checkVictory();
    }

    private void removeDead() {
        Iterator<Unit> it = units.iterator();
        while (it.hasNext()) {
            Unit u = it.next();
            if (u.isDead()) {
                u.state = UnitState.DEAD;
                for (IntelBoard b : intel.values()) b.forget(u);
                for (Unit other : units) {
                    if (other.currentTarget == u) other.currentTarget = null;
                }
                it.remove();
            }
        }
    }

    private void checkVictory() {
        Faction loser = null;
        for (Faction f : Faction.values()) {
            int init = initialCountOf(f);
            if (init <= 0) continue;
            int alive = countAlive(f);
            double lossRatio = 1.0 - (double) alive / init;
            if (lossRatio >= 0.9) {
                loser = f;
                break;
            }
        }
        if (loser != null) {
            for (Faction f : Faction.values()) {
                if (f != loser) { winner = f; break; }
            }
            // 失败方剩余单位停止行动
            for (Unit u : units) {
                u.path = null;
                u.moveGoal = null;
            }
        }
    }

    // ---- AI 辅助 ----

    /** 把每个友方单位所在区域标记为"刚刚访问过" */
    private void markFriendlyRegions() {
        for (Unit u : units) {
            if (u.isDead()) continue;
            exploration.markVisited(u.faction, u.x, u.y, tick);
        }
    }

    /** 半径 r 像素内的单位列表 */
    public List<Unit> unitsWithin(double px, double py, double radius) {
        List<Unit> result = new ArrayList<>();
        double r2 = radius * radius;
        for (Unit u : units) {
            double dx = u.x - px;
            double dy = u.y - py;
            if (dx * dx + dy * dy <= r2) result.add(u);
        }
        return Collections.unmodifiableList(result);
    }
}
