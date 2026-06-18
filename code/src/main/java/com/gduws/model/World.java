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
    /** 单位死亡后留下的残骸，保留到游戏结束（reset 时清空） */
    public final List<Wreckage> wreckages = new ArrayList<>();
    /** 飞行中的射弹（FR-21）：由攻击生成，到达落点后结算伤害 */
    public final List<Projectile> projectiles = new ArrayList<>();

    private final Map<Faction, IntelBoard> intel = new EnumMap<>(Faction.class);
    private final VisionSystem visionSystem = new VisionSystem();
    private final MovementSystem movementSystem = new MovementSystem();
    private final AISystem aiSystem = new AISystem();
    private final CombatSystem combatSystem = new CombatSystem();
    private final ProjectileSystem projectileSystem = new ProjectileSystem();
    private final Pathfinder pathfinder;
    private final ExplorationMap exploration;
    private int tick = 0;

    // 胜负判定
    /** 敌我双方长时间都无兵力损失（僵持）超过该 tick 数则提前判定结束（30 tick/s，约 50 秒） */
    private static final int STALEMATE_TIMEOUT = 2500;
    /** 情报记忆时长：敌人超过该 tick 数未被任一友方单位再次目击，则从情报板移除 */
    private static final int INTEL_MEMORY_TIMEOUT = 300;
    private final Map<Faction, Integer> initialCount = new EnumMap<>(Faction.class);
    private Faction winner;
    private VictoryReason victoryReason;
    private boolean battleStarted;
    private int totalAlive; // 上一帧双方存活总数，用于检测兵力损失
    private int lastLossTick; // 最近一次出现兵力损失的 tick

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

    /** 发射一枚射弹（由 {@link CombatSystem} 开火时调用） */
    public void addProjectile(Projectile p) {
        projectiles.add(p);
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

    /** 本局结算原因（全歼 / 损失超 90% / 僵持超时）；未分出胜负前为 null。 */
    public VictoryReason victoryReason() {
        return victoryReason;
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
        victoryReason = null;
        battleStarted = true;
        totalAlive = countAlive(Faction.PLAYER) + countAlive(Faction.ENEMY);
        lastLossTick = tick;
    }

    /** 重置到布兵前状态：清空所有单位、情报、探索记录、tick */
    public void reset() {
        units.clear();
        wreckages.clear();
        projectiles.clear();
        for (Faction f : Faction.values()) {
            intel.get(f).clearAll();
        }
        exploration.clear();
        combatSystem.recentShots.clear();
        initialCount.clear();
        winner = null;
        victoryReason = null;
        battleStarted = false;
        totalAlive = 0;
        lastLossTick = 0;
        tick = 0;
    }

    /** 推进一逻辑帧：视野 → AI → 移动 → 战斗 → 射弹 → 清理 → 胜负 */
    public void tick() {
        if (winner != null)
            return;
        tick++;
        visionSystem.update(this);
        // 视野更新后剔除过期敌情：脱离己方视野（迷雾中）超时的敌人不再可被索敌
        for (IntelBoard b : intel.values()) {
            b.expireStale(tick, INTEL_MEMORY_TIMEOUT);
        }
        markFriendlyRegions();
        aiSystem.update(this);
        movementSystem.update(this);
        combatSystem.update(this);
        projectileSystem.update(this);
        removeDead();
        if (battleStarted)
            checkVictory();
    }

    private void removeDead() {
        Iterator<Unit> it = units.iterator();
        while (it.hasNext()) {
            Unit u = it.next();
            if (u.isDead()) {
                u.state = UnitState.DEAD;
                // 生成残骸：由 spritePath 推导 _dead.png 路径
                if (u.def.spritePath != null) {
                    String deadPath = u.def.spritePath.replace(".png", "_dead.png");
                    wreckages.add(new Wreckage(u.x, u.y, u.facing, deadPath));
                }
                for (IntelBoard b : intel.values())
                    b.forget(u);
                for (Unit other : units) {
                    if (other.currentTarget == u)
                        other.currentTarget = null;
                }
                it.remove();
            }
        }
    }

    private void checkVictory() {
        // 损失检测：只要双方存活总数下降，刷新“最近损失”时间戳
        int nowAlive = countAlive(Faction.PLAYER) + countAlive(Faction.ENEMY);
        if (nowAlive < totalAlive) {
            lastLossTick = tick;
        }
        totalAlive = nowAlive;

        Faction loser = null;
        VictoryReason reason = null;
        for (Faction f : Faction.values()) {
            int init = initialCountOf(f);
            if (init <= 0)
                continue;
            int alive = countAlive(f);
            double lossRatio = 1.0 - (double) alive / init;
            if (lossRatio > 0.9) {
                loser = f;
                // 全部被歼灭 vs. 仍有零星残存，区分结算文案
                reason = (alive <= 0) ? VictoryReason.ANNIHILATION : VictoryReason.ATTRITION;
                break;
            }
        }
        // 僵持超时：双方长时间无兵力损失，按损失率提前判定
        if (loser == null && tick - lastLossTick >= STALEMATE_TIMEOUT) {
            loser = higherLossFaction();
            reason = VictoryReason.STALEMATE;
        }
        if (loser != null) {
            victoryReason = reason;
            for (Faction f : Faction.values()) {
                if (f != loser) {
                    winner = f;
                    break;
                }
            }
            // 失败方剩余单位停止行动
            for (Unit u : units) {
                u.path = null;
                u.moveGoal = null;
            }
        }
    }

    /** 按损失率选出失败方（损失率高者负）；相等时玩家获胜，故返回敌方 */
    private Faction higherLossFaction() {
        double playerLoss = lossRatioOf(Faction.PLAYER);
        double enemyLoss = lossRatioOf(Faction.ENEMY);
        return enemyLoss >= playerLoss ? Faction.ENEMY : Faction.PLAYER;
    }

    private double lossRatioOf(Faction f) {
        int init = initialCountOf(f);
        if (init <= 0)
            return 0.0;
        return 1.0 - (double) countAlive(f) / init;
    }

    // ---- AI 辅助 ----

    /** 把每个友方单位所在区域标记为"刚刚访问过" */
    private void markFriendlyRegions() {
        for (Unit u : units) {
            if (u.isDead())
                continue;
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
            if (dx * dx + dy * dy <= r2)
                result.add(u);
        }
        return Collections.unmodifiableList(result);
    }
}
