package com.gduws.testkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.gduws.model.AttackProfile;
import com.gduws.model.Faction;
import com.gduws.model.GameMap;
import com.gduws.model.MovementType;
import com.gduws.model.TerrainType;
import com.gduws.model.Tile;
import com.gduws.model.Unit;
import com.gduws.model.UnitDef;
import com.gduws.model.World;

/**
 * 测试夹具：纯代码构造地图 / 单位定义 / 单位 / 世界，避免单元测试依赖磁盘文件。
 *
 * <p>所有方法均为静态工厂，命名贴合被测领域语义。集成测试用到的真实数据目录定位见 {@link #dataRoot()}。</p>
 */
public final class Fixtures {

    private Fixtures() { }

    public static final int TILE = 20;

    // ---- 地图构造 ----

    /** 全 GRASS（LAND 可通行）地图，每格均可布兵。 */
    public static GameMap landMap(int cols, int rows) {
        return filledMap(cols, rows, TILE, TerrainType.GRASS);
    }

    /** 全 WATER（WATER / UNDERWATER 可通行）地图。 */
    public static GameMap waterMap(int cols, int rows) {
        return filledMap(cols, rows, TILE, TerrainType.WATER);
    }

    /** 用单一地形填充的地图。 */
    public static GameMap filledMap(int cols, int rows, int tileSize, TerrainType fill) {
        Tile[][] tiles = new Tile[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                tiles[r][c] = new Tile(fill);
            }
        }
        return new GameMap(cols, rows, tileSize, tiles);
    }

    /**
     * 被一道竖直 MOUNTAIN 墙（LAND 不可通行）分隔成左右两块陆地的地图。
     * 用于"双方永不相遇"的胜负判定测试（90% 损失 / 僵持超时）。
     */
    public static GameMap splitLandMap(int cols, int rows, int wallStartCol, int wallWidth) {
        GameMap m = landMap(cols, rows);
        for (int r = 0; r < rows; r++) {
            for (int w = 0; w < wallWidth; w++) {
                int c = wallStartCol + w;
                if (c >= 0 && c < cols) {
                    m.tileAt(c, r).terrain = TerrainType.MOUNTAIN;
                }
            }
        }
        return m;
    }

    public static void setTerrain(GameMap m, int col, int row, TerrainType t) {
        m.tileAt(col, row).terrain = t;
    }

    public static void setDeployable(GameMap m, int col, int row, boolean deployable) {
        m.tileAt(col, row).deployable = deployable;
    }

    // ---- 单位定义构造 ----

    public static AttackProfile attack(boolean land, boolean waterSurface, boolean air,
                                       boolean underwater, int range, int damage, int cooldown) {
        AttackProfile ap = new AttackProfile();
        ap.canAttackLand = land;
        ap.canAttackWaterSurface = waterSurface;
        ap.canAttackAir = air;
        ap.canAttackUnderwater = underwater;
        ap.maxAttackRange = range;
        ap.directDamage = damage;
        ap.shootDelay = cooldown;
        return ap;
    }

    public static UnitDef def(String id, MovementType mt, int hp, double radius,
                              double speed, int sight, AttackProfile attack) {
        UnitDef d = new UnitDef();
        d.id = id;
        d.displayName = id;
        d.maxHp = hp;
        d.radius = radius;
        d.movementType = mt;
        d.moveSpeed = speed;
        d.sightRange = sight;
        d.attack = attack;
        return d;
    }

    /** 仿轻型坦克：LAND，可打陆 / 水面，射程 130，伤害 25，冷却 75。 */
    public static UnitDef landTank() {
        return def("light_tank", MovementType.LAND, 210, 11, 1.1, 120,
                attack(true, true, false, false, 130, 25, 75));
    }

    /** 一种无武装的陆地侦察单位（attack 各位为 false）。 */
    public static UnitDef unarmedLand() {
        return def("recon", MovementType.LAND, 100, 8, 2.0, 120,
                attack(false, false, false, false, 0, 0, 1));
    }

    /** 仅打陆地的目标定义（用于攻击域命中测试中的"陆地层目标"）。 */
    public static UnitDef layerTarget(String id, MovementType mt) {
        return def(id, mt, 100, 10, 1.0, 100, attack(false, false, false, false, 0, 0, 1));
    }

    // ---- 单位 / 世界 ----

    public static Unit unit(UnitDef def, Faction f, double x, double y) {
        return new Unit(def, f, x, y);
    }

    /** 在 (col,row) 格中心放置一个单位。 */
    public static Unit unitAtCell(GameMap m, UnitDef def, Faction f, int col, int row) {
        return new Unit(def, f, m.cellCenterX(col), m.cellCenterY(row));
    }

    /**
     * 构造一个左右两块陆地被高墙隔开、双方各若干 STRIKE 陆地单位的世界（未 startBattle）。
     * 双方相距远大于视野，且陆地单位无法越墙，因此永不相遇——便于隔离地测试胜负判定。
     */
    public static World separatedWorld(int players, int enemies) {
        GameMap m = splitLandMap(50, 10, 20, 10); // 墙占 col 20..29（200px > 视野 140）
        World w = new World(m);
        UnitDef d = landTank();
        for (int i = 0; i < players; i++) {
            w.addUnit(unitAtCell(m, d, Faction.PLAYER, i % 18, i / 18));        // 左块 col 0..17
        }
        for (int i = 0; i < enemies; i++) {
            w.addUnit(unitAtCell(m, d, Faction.ENEMY, 30 + i % 18, i / 18));     // 右块 col 30..47
        }
        return w;
    }

    // ---- 真实数据目录定位（供数据驱动集成测试） ----

    /**
     * 定位仓库中的 {@code data/} 目录（含 units / levels / maps）。
     * 兼容从 code/ 或仓库根运行测试两种情形；找不到返回 null（测试据此 assume 跳过）。
     */
    public static Path dataRoot() {
        String[] candidates = {"data", "code/data", "../code/data", "../data"};
        for (String c : candidates) {
            Path p = Paths.get(c);
            if (Files.isDirectory(p.resolve("units")) && Files.isDirectory(p.resolve("levels"))) {
                return p;
            }
        }
        return null;
    }
}
