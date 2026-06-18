package com.gduws.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gduws.model.MovementType;
import com.gduws.model.UnitDef;
import com.gduws.testkit.Fixtures;

/**
 * {@link UnitInfoText} 兵种简介文本格式化单元测试（FR-02 扩展：兵种简介）。
 *
 * <p>纯字符串组装，不触及 Swing，故可在无界面环境直接断言移动域/攻击域中文映射与摘要内容。</p>
 */
@DisplayName("UnitInfoText 兵种简介文本")
class UnitInfoTextTest {

    @Test
    @DisplayName("移动域中文名映射")
    void movementNames() {
        assertEquals("陆地", UnitInfoText.movementName(MovementType.LAND));
        assertEquals("水面", UnitInfoText.movementName(MovementType.WATER));
        assertEquals("空中", UnitInfoText.movementName(MovementType.AIR));
        assertEquals("水下", UnitInfoText.movementName(MovementType.UNDERWATER));
    }

    @Test
    @DisplayName("攻击域：多目标用斜杠连接")
    void attackTargetsMulti() {
        UnitDef tank = Fixtures.landTank(); // 可打陆 + 水面
        assertEquals("陆/水面", UnitInfoText.attackTargets(tank.attack));
    }

    @Test
    @DisplayName("攻击域：无任一目标 → 无武装")
    void attackTargetsNone() {
        UnitDef recon = Fixtures.unarmedLand();
        assertEquals("无武装", UnitInfoText.attackTargets(recon.attack));
    }

    @Test
    @DisplayName("纯文本摘要包含关键字段；showCost 控制价格显示")
    void plainSummary() {
        UnitDef tank = Fixtures.landTank();
        tank.cost = 100;

        String withCost = UnitInfoText.describePlain(tank, true);
        assertTrue(withCost.contains("¥100"), "应含价格");
        assertTrue(withCost.contains("陆地"), "应含移动域");
        assertTrue(withCost.contains("陆/水面"), "应含攻击域");

        String noCost = UnitInfoText.describePlain(tank, false);
        assertTrue(!noCost.contains("¥"), "showCost=false 不应含价格");
    }
}
