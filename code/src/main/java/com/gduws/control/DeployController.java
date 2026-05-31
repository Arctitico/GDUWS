package com.gduws.control;

import java.util.LinkedHashMap;
import java.util.Map;

import com.gduws.data.UnitDefLoader;
import com.gduws.model.Faction;
import com.gduws.model.LevelDef;
import com.gduws.model.Unit;
import com.gduws.model.UnitDef;
import com.gduws.model.UnitRole;
import com.gduws.model.World;

/** 布兵阶段逻辑：管理可用兵力预算、选中单位类型、放置/移除校验。 */
public class DeployController {

    private final World world;
    private final UnitDefLoader unitDefs;
    private final Map<String, Integer> remaining = new LinkedHashMap<>();
    private String selectedUnitId;
    private String lastMessage = "";
    /** 新放置单位的任务角色（侦察 / 打击） */
    private UnitRole deployRole = UnitRole.STRIKE;

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

    public UnitRole deployRole() {
        return deployRole;
    }

    public void setDeployRole(UnitRole role) {
        this.deployRole = role;
        lastMessage = "新单位将作为：" + roleName(role);
    }

    /** 切换指定像素位置处己方单位的任务角色，成功返回 true */
    public boolean toggleRoleAt(double px, double py) {
        Unit u = world.unitAt(px, py, 0);
        if (u == null || u.faction != Faction.PLAYER) {
            return false;
        }
        u.role = (u.role == UnitRole.SCOUT) ? UnitRole.STRIKE : UnitRole.SCOUT;
        lastMessage = u.def.displayName + " 改为：" + roleName(u.role);
        return true;
    }

    private static String roleName(UnitRole role) {
        return role == UnitRole.SCOUT ? "侦察" : "打击";
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
        Unit u = new Unit(def, Faction.PLAYER, cx, cy);
        u.role = deployRole;
        world.addUnit(u);
        remaining.put(selectedUnitId, remaining.get(selectedUnitId) - 1);
        lastMessage = "已放置 " + def.displayName + "（" + roleName(deployRole) + "）";
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
