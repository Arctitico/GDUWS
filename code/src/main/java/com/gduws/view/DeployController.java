package com.gduws.view;

import java.util.LinkedHashMap;
import java.util.Map;

import com.gduws.data.UnitDefLoader;
import com.gduws.model.Faction;
import com.gduws.model.LevelDef;
import com.gduws.model.Unit;
import com.gduws.model.UnitDef;
import com.gduws.model.World;

/** 布兵阶段逻辑：管理可用兵力预算、选中单位类型、放置/移除校验。 */
public class DeployController {

    private final World world;
    private final UnitDefLoader unitDefs;
    private final Map<String, Integer> remaining = new LinkedHashMap<>();
    private String selectedUnitId;
    private String lastMessage = "";

    public DeployController(World world, UnitDefLoader unitDefs, LevelDef level) {
        this.world = world;
        this.unitDefs = unitDefs;
        reset(level);
    }

    /** 重置预算与选中（用于"重新挑战"/返回选关重入） */
    public void reset(LevelDef level) {
        remaining.clear();
        remaining.putAll(level.playerBudget);
        selectedUnitId = remaining.isEmpty() ? null : remaining.keySet().iterator().next();
        lastMessage = "";
    }

    public Map<String, Integer> remaining() {
        return remaining;
    }

    public String selectedUnitId() {
        return selectedUnitId;
    }

    public void selectUnit(String unitId) {
        if (remaining.containsKey(unitId)) {
            selectedUnitId = unitId;
            lastMessage = "已选择：" + unitDefs.get(unitId).displayName;
        }
    }

    public String lastMessage() {
        return lastMessage;
    }

    /** 在像素坐标尝试放置当前选中的己方单位。成功返回 true。 */
    public boolean tryPlace(double px, double py) {
        if (selectedUnitId == null) {
            lastMessage = "请先选择单位类型";
            return false;
        }
        if (remaining.getOrDefault(selectedUnitId, 0) <= 0) {
            lastMessage = "该单位数量已用尽";
            return false;
        }
        UnitDef def = unitDefs.get(selectedUnitId);
        int col = world.map.toCol(px);
        int row = world.map.toRow(py);
        if (!world.map.isPassable(col, row, def.movementType)) {
            lastMessage = "该地形不能放置 " + def.displayName;
            return false;
        }
        if (world.unitAt(px, py, def.radius) != null) {
            lastMessage = "与已有单位重叠";
            return false;
        }
        double cx = world.map.cellCenterX(col);
        double cy = world.map.cellCenterY(row);
        world.addUnit(new Unit(def, Faction.PLAYER, cx, cy));
        remaining.put(selectedUnitId, remaining.get(selectedUnitId) - 1);
        lastMessage = "已放置 " + def.displayName;
        return true;
    }

    /** 在像素坐标尝试移除一个己方单位（返还预算）。成功返回 true。 */
    public boolean tryRemove(double px, double py) {
        Unit u = world.unitAt(px, py, 0);
        if (u == null || u.faction != Faction.PLAYER) {
            return false;
        }
        world.removeUnit(u);
        remaining.merge(u.def.id, 1, Integer::sum);
        lastMessage = "已移除 " + u.def.displayName;
        return true;
    }

    public UnitDef defOf(String unitId) {
        return unitDefs.get(unitId);
    }
}
