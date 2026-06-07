package com.gduws.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gduws.control.DeployController;
import com.gduws.data.UnitDefLoader;
import com.gduws.model.Faction;
import com.gduws.model.LevelDef;
import com.gduws.model.Unit;
import com.gduws.model.UnitRole;
import com.gduws.model.World;
import com.gduws.testkit.Fixtures;

/**
 * 集成测试：布兵 → 开始战斗 → 自动推演 → 结算 全流程（FR-02 → FR-03 → FR-04）。
 *
 * <p>用 {@link DeployController} 在世界中布置玩家单位，再 startBattle 并推进至结算，
 * 验证控制层与模型层协同的端到端阶段流转。单位定义经临时 JSON 加载。</p>
 */
@DisplayName("集成：布兵→战斗→结算 流程")
class DeployToBattleIT {

    private static final String LIGHT_TANK_JSON = """
        { "id":"light_tank", "displayName":"轻型坦克", "maxHp":210, "radius":11,
          "movementType":"LAND", "moveSpeed":1.1, "sightRange":120,
          "attack": { "canAttackLand":true, "canAttackWaterSurface":true,
                      "maxAttackRange":130, "directDamage":25, "shootDelay":75 } }
        """;

    private UnitDefLoader loader;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("light_tank.json"), LIGHT_TANK_JSON, StandardCharsets.UTF_8);
        loader = new UnitDefLoader();
        loader.loadDirectory(tmp);
    }

    @Test
    @DisplayName("布兵置入 PLAYER 单位 → startBattle 记录初始数 → 推演至玩家获胜")
    void deployThenBattleResolvesToVictory() {
        World w = new World(Fixtures.landMap(30, 30));
        LevelDef level = new LevelDef();
        level.playerBudget.put("light_tank", 3);
        DeployController deploy = new DeployController(w, loader, level);

        // FR-02：1 侦察 + 2 打击（格间距 ≥ 2 以免半径 11 的单位放置重叠）
        deploy.setDeployRole(UnitRole.SCOUT);
        assertTrue(deploy.tryPlace(w.map.cellCenterX(3), w.map.cellCenterY(12)));
        deploy.setDeployRole(UnitRole.STRIKE);
        assertTrue(deploy.tryPlace(w.map.cellCenterX(3), w.map.cellCenterY(10)));
        assertTrue(deploy.tryPlace(w.map.cellCenterX(3), w.map.cellCenterY(14)));

        assertEquals(3, w.units.size());
        assertEquals(0, deploy.remaining().get("light_tank"), "预算应已用尽");
        for (Unit u : w.units) {
            assertEquals(Faction.PLAYER, u.faction);
        }

        // 敌方 1 单位（远在东侧）
        w.addUnit(Fixtures.unitAtCell(w.map, loader.get("light_tank"), Faction.ENEMY, 26, 12));

        // FR-03：开始战斗，记录初始兵力基线
        w.startBattle();
        assertEquals(3, w.initialCountOf(Faction.PLAYER));
        assertEquals(1, w.initialCountOf(Faction.ENEMY));

        // FR-04：推进至结算（3 v 1 玩家胜），且全程不抛异常
        Faction winner = assertDoesNotThrow(() -> {
            for (int t = 0; t < 8000; t++) {
                w.tick();
                if (w.winner() != null) {
                    return w.winner();
                }
            }
            return w.winner();
        });

        assertNotNull(winner, "应在合理时间内结算");
        assertEquals(Faction.PLAYER, winner);
    }
}
