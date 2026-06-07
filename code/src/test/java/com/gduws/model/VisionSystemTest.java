package com.gduws.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.testkit.Fixtures;

/**
 * {@link VisionSystem} 视野扫描单元测试。
 *
 * <p>关联：BR-03-1（视野内敌方写入本阵营情报板）、FR-03 验收"视野内存在敌方 → 写入情报板"。</p>
 */
@DisplayName("VisionSystem 视野扫描")
class VisionSystemTest {

    private final VisionSystem vision = new VisionSystem();

    private World worldWith(Unit... units) {
        World w = new World(Fixtures.landMap(40, 40)); // 800×800，坐标空间充裕
        for (Unit u : units) {
            w.addUnit(u);
        }
        return w;
    }

    @Test
    @DisplayName("视野内敌方 → 写入观察者所属阵营情报板")
    void enemyWithinSightIsReported() {
        Unit observer = Fixtures.unit(Fixtures.landTank(), Faction.PLAYER, 110, 110); // sight 120
        Unit enemy = Fixtures.unit(Fixtures.unarmedLand(), Faction.ENEMY, 110, 210);  // 距离 100 < 120
        World w = worldWith(observer, enemy);

        vision.update(w);

        assertTrue(w.intelOf(Faction.PLAYER).hasAnyEnemy());
        assertSame(enemy, w.intelOf(Faction.PLAYER).knownEnemies().iterator().next().enemy);
    }

    @Test
    @DisplayName("视野外敌方 → 不写入情报板")
    void enemyBeyondSightIsNotReported() {
        Unit observer = Fixtures.unit(Fixtures.landTank(), Faction.PLAYER, 110, 110);
        Unit enemy = Fixtures.unit(Fixtures.unarmedLand(), Faction.ENEMY, 110, 360);   // 距离 250 > 120
        World w = worldWith(observer, enemy);

        vision.update(w);

        assertFalse(w.intelOf(Faction.PLAYER).hasAnyEnemy());
    }

    @Test
    @DisplayName("友方单位不计入敌情")
    void friendlyIsNotReported() {
        Unit observer = Fixtures.unit(Fixtures.landTank(), Faction.PLAYER, 110, 110);
        Unit friend = Fixtures.unit(Fixtures.landTank(), Faction.PLAYER, 120, 110);    // 同阵营近距
        World w = worldWith(observer, friend);

        vision.update(w);

        assertFalse(w.intelOf(Faction.PLAYER).hasAnyEnemy());
    }

    @Test
    @DisplayName("双方互相目击 → 各自情报板都记录对方")
    void mutualSightingPopulatesBothBoards() {
        Unit player = Fixtures.unit(Fixtures.landTank(), Faction.PLAYER, 110, 110);
        Unit enemy = Fixtures.unit(Fixtures.landTank(), Faction.ENEMY, 150, 110);      // 距离 40
        World w = worldWith(player, enemy);

        vision.update(w);

        assertTrue(w.intelOf(Faction.PLAYER).hasAnyEnemy(), "玩家情报板应记录敌方");
        assertTrue(w.intelOf(Faction.ENEMY).hasAnyEnemy(), "敌方情报板应记录玩家");
    }
}
