package com.gduws.model;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 单阵营已知敌情。打击单位不依赖自身视野，而查询本阵营 {@code IntelBoard} 选目标，
 * 由此打通"侦察发现 → 共享情报 → 打击行动"的链路。
 */
public class IntelBoard {

    private final Map<Unit, IntelEntry> known = new HashMap<>();

    /** 上报一次目击：更新位置与时间戳。 */
    public void report(Unit enemy, double x, double y, int tick) {
        IntelEntry e = known.get(enemy);
        if (e == null) {
            known.put(enemy, new IntelEntry(enemy, x, y, tick));
        } else {
            e.x = x;
            e.y = y;
            e.lastSeenTick = tick;
        }
    }

    /** 敌方单位死亡或永久消失时调用。 */
    public void forget(Unit enemy) {
        known.remove(enemy);
    }

    /** 清空所有已知敌情（用于重置） */
    public void clearAll() {
        known.clear();
    }

    public boolean hasAnyEnemy() {
        return !known.isEmpty();
    }

    public Collection<IntelEntry> knownEnemies() {
        return Collections.unmodifiableCollection(known.values());
    }

    /** 单条已知敌情：目标引用 + 最近一次目击位置/时间。 */
    public static final class IntelEntry {
        public final Unit enemy;
        public double x, y;
        public int lastSeenTick;

        public IntelEntry(Unit enemy, double x, double y, int lastSeenTick) {
            this.enemy = enemy;
            this.x = x;
            this.y = y;
            this.lastSeenTick = lastSeenTick;
        }
    }
}
