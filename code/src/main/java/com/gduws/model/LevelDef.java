package com.gduws.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 关卡定义：地图路径、玩家可用兵力预算、敌方预置单位。 */
public class LevelDef {

    /** 敌方预置单位（按格坐标放置）。 */
    public static class PlacedUnit {
        public final String unitId;
        public final int col;
        public final int row;
        public final UnitRole role;

        public PlacedUnit(String unitId, int col, int row, UnitRole role) {
            this.unitId = unitId;
            this.col = col;
            this.row = row;
            this.role = role;
        }
    }

    public String id;
    public String name;
    public String mapPath;
    /**
     * 玩家总资金（资金制关卡，FR-02 扩展）：&gt;0 时玩家按兵种价格自由组合，受 {@link #playerBudget} 计数上限约束；
     * &le;0（缺省）时退化为纯计数制——只看 {@link #playerBudget} 给定的固定数量，不消耗资金。
     */
    public int playerFunds = 0;
    /** 玩家可用单位与数量上限（计数制即固定数量；资金制即每种兵的购买上限）。 */
    public final Map<String, Integer> playerBudget = new LinkedHashMap<>();
    /** 敌方预置单位。 */
    public final List<PlacedUnit> enemyUnits = new ArrayList<>();
}
