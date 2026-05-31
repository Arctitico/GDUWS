package com.gduws.control;

import com.gduws.data.UnitDefLoader;
import com.gduws.model.Faction;
import com.gduws.model.LevelDef;
import com.gduws.model.Unit;
import com.gduws.model.UnitDef;
import com.gduws.model.World;

/** 关卡装配：把关卡定义中的敌方预置单位实例化并放入世界 */
public final class BattleSetup {

    private final UnitDefLoader unitDefs;

    public BattleSetup(UnitDefLoader unitDefs) {
        this.unitDefs = unitDefs;
    }

    /** 按关卡配置在世界中放置全部敌方单位（坐标按格中心换算） */
    public void placeEnemies(World world, LevelDef level) {
        for (LevelDef.PlacedUnit p : level.enemyUnits) {
            UnitDef def = unitDefs.get(p.unitId);
            double cx = world.map.cellCenterX(p.col);
            double cy = world.map.cellCenterY(p.row);
            Unit u = new Unit(def, Faction.ENEMY, cx, cy);
            u.role = p.role;
            world.addUnit(u);
        }
    }
}
