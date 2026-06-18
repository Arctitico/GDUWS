package com.gduws.view;

import com.gduws.model.AttackProfile;
import com.gduws.model.MovementType;
import com.gduws.model.UnitDef;

/**
 * 兵种简介文本格式化（FR-02 扩展：兵种简介）。
 *
 * <p>把 {@link UnitDef} 的属性翻译成中文可读文本，供布兵阶段的兵种简介面板展示。
 * 本类只做字符串组装、不依赖 Swing/AWT，因此可被单元测试直接覆盖。</p>
 */
public final class UnitInfoText {

    private UnitInfoText() { }

    /** 移动域中文名。 */
    public static String movementName(MovementType mt) {
        switch (mt) {
            case LAND:       return "陆地";
            case WATER:      return "水面";
            case AIR:        return "空中";
            case UNDERWATER: return "水下";
            default:         return mt.name();
        }
    }

    /** 攻击域（可打击的目标层）中文描述；无任一攻击域时返回"无武装"。 */
    public static String attackTargets(AttackProfile a) {
        if (a == null) {
            return "无武装";
        }
        StringBuilder sb = new StringBuilder();
        if (a.canAttackLand)         append(sb, "陆");
        if (a.canAttackWaterSurface) append(sb, "水面");
        if (a.canAttackAir)          append(sb, "空中");
        if (a.canAttackUnderwater)   append(sb, "水下");
        return sb.length() == 0 ? "无武装" : sb.toString();
    }

    private static void append(StringBuilder sb, String s) {
        if (sb.length() > 0) {
            sb.append('/');
        }
        sb.append(s);
    }

    /**
     * 单行纯文本摘要（便于测试与日志）：名称、移动域、HP、视野、攻击域、射程、伤害。
     * {@code showCost} 为真时附带价格。
     */
    public static String describePlain(UnitDef def, boolean showCost) {
        StringBuilder sb = new StringBuilder();
        sb.append(def.displayName);
        if (showCost) {
            sb.append(" ¥").append(def.cost);
        }
        sb.append(" | 移动域:").append(movementName(def.movementType));
        sb.append(" | HP:").append(def.maxHp);
        sb.append(" | 视野:").append(def.sightRange);
        AttackProfile a = def.attack;
        sb.append(" | 攻击域:").append(attackTargets(a));
        if (a != null && (a.maxAttackRange > 0 || a.directDamage > 0)) {
            sb.append(" | 射程:").append(a.maxAttackRange);
            sb.append(" | 伤害:").append(a.directDamage);
        }
        return sb.toString();
    }

    /** 富文本（HTML）卡片，用于在简介面板中展示单个兵种的全部关键属性。 */
    public static String describeHtml(UnitDef def, boolean showCost) {
        StringBuilder sb = new StringBuilder("<html><b>").append(def.displayName).append("</b>");
        if (showCost) {
            sb.append("　价格 ¥").append(def.cost);
        }
        sb.append("<br>移动域：").append(movementName(def.movementType))
          .append("　移速：").append(trim(def.moveSpeed));
        sb.append("<br>生命：").append(def.maxHp)
          .append("　视野：").append(def.sightRange);
        AttackProfile a = def.attack;
        sb.append("<br>攻击域：").append(attackTargets(a));
        if (a != null && (a.maxAttackRange > 0 || a.directDamage > 0)) {
            sb.append("<br>射程：").append(a.maxAttackRange)
              .append("　伤害：").append(a.directDamage)
              .append("　冷却：").append(a.shootDelay);
            if (a.splashRadius > 0) {
                sb.append("　溅射半径：").append(a.splashRadius);
            }
        }
        sb.append("</html>");
        return sb.toString();
    }

    /** 去掉浮点尾随的 .0，使 1.0 显示为 "1"、1.1 仍为 "1.1"。 */
    private static String trim(double v) {
        if (v == Math.rint(v)) {
            return Integer.toString((int) v);
        }
        return Double.toString(v);
    }
}
