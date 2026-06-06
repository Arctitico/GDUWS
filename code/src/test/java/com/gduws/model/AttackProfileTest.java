package com.gduws.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.model.Faction;
import com.gduws.testkit.Fixtures;

/**
 * {@link AttackProfile} 攻击域命中逻辑单元测试。
 *
 * <p>用按 §5.1.2 攻击域矩阵手写的合成攻击域，逐单位 × 逐目标层校验 {@link AttackProfile#canTarget}，
 * 验证"攻击域 → 目标高度层"的克制语义。本测试针对代码逻辑，独立于磁盘数据文件。</p>
 */
@DisplayName("AttackProfile 攻击域命中（§5.1.2 矩阵语义）")
class AttackProfileTest {

    // 四类高度层的目标单位（layer() 由 movementType 推导）
    private final Unit landTarget  = Fixtures.unit(Fixtures.layerTarget("t_land",  MovementType.LAND),       Faction.ENEMY, 0, 0);
    private final Unit waterTarget = Fixtures.unit(Fixtures.layerTarget("t_water", MovementType.WATER),      Faction.ENEMY, 0, 0);
    private final Unit airTarget   = Fixtures.unit(Fixtures.layerTarget("t_air",   MovementType.AIR),        Faction.ENEMY, 0, 0);
    private final Unit subTarget   = Fixtures.unit(Fixtures.layerTarget("t_sub",   MovementType.UNDERWATER), Faction.ENEMY, 0, 0);

    /** 单位攻击域定义行：名称 + 能否打 [陆, 水面, 空, 水下]。 */
    private record Row(String name, boolean land, boolean water, boolean air, boolean under) { }

    /** §5.1.2 攻击域矩阵（规格基线值）。 */
    private static final Row[] SPEC_MATRIX = {
        new Row("轻型坦克", true,  true,  false, false),
        new Row("重型坦克", true,  true,  true,  false),
        new Row("拦截机",   false, false, true,  false),
        new Row("攻击机",   true,  true,  false, false),
        new Row("战列舰",   false, true,  false, false),
        new Row("驱逐舰",   false, true,  true,  true),
        new Row("潜艇",     false, true,  false, true),
    };

    @Test
    @DisplayName("逐单位 × 逐目标层 校验攻击域矩阵")
    void canTargetFollowsSpecMatrix() {
        for (Row r : SPEC_MATRIX) {
            AttackProfile ap = Fixtures.attack(r.land(), r.water(), r.air(), r.under(), 100, 10, 10);
            assertAll(r.name(),
                () -> assertEquals(r.land(),  ap.canTarget(landTarget),  r.name() + " → 陆地目标"),
                () -> assertEquals(r.water(), ap.canTarget(waterTarget), r.name() + " → 水面目标"),
                () -> assertEquals(r.air(),   ap.canTarget(airTarget),   r.name() + " → 空中目标"),
                () -> assertEquals(r.under(), ap.canTarget(subTarget),   r.name() + " → 水下目标"));
        }
    }

    @Test
    @DisplayName("canAttackAnything：任一域为真则有攻击能力，全假则无")
    void canAttackAnything() {
        assertFalse(Fixtures.attack(false, false, false, false, 0, 0, 1).canAttackAnything());
        assertTrue(Fixtures.attack(false, false, false, true, 1, 1, 1).canAttackAnything());
        assertTrue(Fixtures.attack(true, false, false, false, 1, 1, 1).canAttackAnything());
    }
}
