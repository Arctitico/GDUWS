package com.gduws.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;

import java.awt.image.BufferedImage;

import java.util.HashSet;
import java.util.Set;

import com.gduws.model.CombatSystem;
import com.gduws.model.Decoration;
import com.gduws.model.Faction;
import com.gduws.model.GameMap;
import com.gduws.model.IntelBoard;
import com.gduws.model.MovementType;
import com.gduws.model.TerrainType;
import com.gduws.model.Tile;
import com.gduws.model.Unit;
import com.gduws.model.World;
import com.gduws.model.Wreckage;

/** 渲染战场：地形网格 + 单位 + 覆盖层（攻击范围/路径/已知敌情）。 */
public class GameRenderer {

    private static final Color GRASS_COLOR    = new Color(96, 152, 84);
    private static final Color DIRT_COLOR     = new Color(134, 98, 66);
    private static final Color SAND_COLOR     = new Color(214, 190, 140);
    private static final Color MOUNTAIN_COLOR = new Color(120, 116, 112);
    private static final Color SHALLOW_COLOR  = new Color(86, 150, 200);
    private static final Color WATER_COLOR    = new Color(58, 110, 176);
    private static final Color DEEP_COLOR     = new Color(36, 78, 140);
    private static final Color GRID_COLOR     = new Color(0, 0, 0, 40);

    private static final Color PLAYER_COLOR = new Color(70, 130, 220);
    private static final Color ENEMY_COLOR  = new Color(210, 70, 70);

    private static final Color PATH_COLOR         = new Color(255, 255, 255, 160);
    private static final Color ATTACK_RANGE_COLOR = new Color(255, 255, 255, 200);
    private static final Color INTEL_COLOR        = new Color(255, 200, 0, 220);
    private static final Color SELECT_COLOR       = new Color(255, 235, 90, 230);

    /** 是否绘制玩家方覆盖层（攻击范围、路径、已知敌情）。 */
    public boolean showOverlay = true;
    /** 战争迷雾模式：布兵阶段 DEPLOY、战斗阶段 BATTLE，其余 NONE。 */
    public FogRenderer.Mode fogMode = FogRenderer.Mode.NONE;
    /** 当前选中的单位集合（可框选多个，用于高亮与覆盖层过滤）。 */
    public final Set<Unit> selectedUnits = new HashSet<>();
    /** 战斗阶段仅绘制被选中单位的路径；布兵阶段绘制全部己方单位。（攻击范围始终仅对选中单位绘制，不受此开关控制。） */
    public boolean overlayOnlySelected = false;

    private final SpriteCache sprites = new SpriteCache();
    private final TerrainTextures terrain = new TerrainTextures();
    private final FogRenderer fog = new FogRenderer();

    /**
     * 该单位是否应被战争迷雾隐藏（不渲染）。
     * <ul>
     *   <li>布兵阶段（DEPLOY）：隐藏全部敌方预置单位（FR-11）</li>
     *   <li>战斗阶段（BATTLE）：隐藏未落入己方视野并集的敌方单位</li>
     * </ul>
     * 己方单位始终可见。
     */
    private boolean hiddenByFog(World world, Unit u) {
        if (u.faction == Faction.PLAYER) return false;
        if (fogMode == FogRenderer.Mode.DEPLOY) return true;
        if (fogMode == FogRenderer.Mode.BATTLE) return !visibleInBattle(world, u.x, u.y);
        return false;
    }

    /** 战斗迷雾下：该世界坐标是否落在己方任一单位的视野范围内 */
    private boolean visibleInBattle(World world, double px, double py) {
        for (Unit u : world.units) {
            if (u.faction != Faction.PLAYER || u.isDead()) continue;
            int sight = u.def.sightRange;
            if (sight <= 0) continue;
            double dx = u.x - px;
            double dy = u.y - py;
            if (dx * dx + dy * dy <= (double) sight * sight) {
                return true;
            }
        }
        return false;
    }

    public void render(Graphics2D g, World world) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawTerrain(g, world.map);

        drawWreckages(g, world);

        if (showOverlay) {
            drawAttackRangeCircles(g, world);
            drawPaths(g, world);
        }

        drawShots(g, world);

        // 战争迷雾：覆盖在地形/残骸/己方覆盖层之上，再在其上绘制可见单位
        if (fogMode != FogRenderer.Mode.NONE) {
            fog.render(g, world, fogMode);
        }

        for (Unit u : world.units) {
            // 迷雾隐藏的敌方单位（布兵期全部、战斗期视野外）不渲染
            if (hiddenByFog(world, u)) continue;
            drawUnit(g, u);
        }
        if (!selectedUnits.isEmpty()) {
            drawSelectionRings(g, world);
        }
        for (Unit u : world.units) {
            if (hiddenByFog(world, u)) continue;
            drawHpBar(g, u);
        }

        if (showOverlay) {
            drawIntel(g, world, Faction.PLAYER);
        }
    }


    private void drawTerrain(Graphics2D g, GameMap map) {
        int ts = map.tileSize;
        // 地形纹理（无素材时回退纯色块）
        for (int r = 0; r < map.rows; r++) {
            for (int c = 0; c < map.cols; c++) {
                Tile tile = map.tileAt(c, r);
                BufferedImage tex = terrain.terrain(tile.terrain);
                if (tex != null) {
                    g.drawImage(tex, c * ts, r * ts, ts, ts, null);
                } else {
                    g.setColor(colorOf(tile.terrain));
                    g.fillRect(c * ts, r * ts, ts, ts);
                }
            }
        }
        // 装饰物覆盖层（树木 / 灌木 / 岩石）
        for (int r = 0; r < map.rows; r++) {
            for (int c = 0; c < map.cols; c++) {
                Decoration deco = map.tileAt(c, r).decoration;
                if (deco == null) continue;
                BufferedImage img = terrain.decoration(deco);
                if (img != null) {
                    g.drawImage(img, c * ts, r * ts, ts, ts, null);
                }
            }
        }
        // 网格线
        g.setColor(GRID_COLOR);
        for (int c = 0; c <= map.cols; c++) {
            g.drawLine(c * ts, 0, c * ts, map.pixelHeight());
        }
        for (int r = 0; r <= map.rows; r++) {
            g.drawLine(0, r * ts, map.pixelWidth(), r * ts);
        }
    }

    /** 绘制所有单位残骸（在地形之上、活单位之下） */
    private void drawWreckages(Graphics2D g, World world) {
        for (Wreckage w : world.wreckages) {
            sprites.draw(g, w.deadSpritePath, w.x, w.y, w.facing);
        }
    }

    private void drawUnit(Graphics2D g, Unit u) {
        Color base = (u.faction == Faction.PLAYER) ? PLAYER_COLOR : ENEMY_COLOR;
        double r = Math.max(6, u.def.radius);
        int d = (int) (r * 2);
        int x = (int) (u.x - r);
        int y = (int) (u.y - r);

        // 空中单位：先画地面阴影体现高度层
        if (u.def.movementType == MovementType.AIR) {
            g.setColor(new Color(0, 0, 0, 60));
            g.fillOval(x + 4, y + 6, d, d);
        }

        // 仅以 PNG 原始尺寸绘制单位图像；失败时回退为色块圆
        boolean drawn = sprites.draw(g, u.def.spritePath, u.x, u.y, u.facing);
        if (!drawn) {
            g.setColor(base);
            g.fillOval(x, y, d, d);
            g.setColor(base.darker());
            g.setStroke(new BasicStroke(2f));
            g.drawOval(x, y, d, d);
            // 朝向指示线
            int fx = (int) (u.x + Math.cos(u.facing) * r);
            int fy = (int) (u.y + Math.sin(u.facing) * r);
            g.drawLine((int) u.x, (int) u.y, fx, fy);
        }

        // 炮塔覆盖在底座之上，按各自的朝向绘制（底座随移动转向、炮塔随攻击目标转向）
        if (u.def.turretSpritePath != null) {
            sprites.draw(g, u.def.turretSpritePath, u.x, u.y, u.turretFacing);
        }
    }

    private void drawSelectionRings(Graphics2D g, World world) {
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(2f));
        g.setColor(SELECT_COLOR);
        for (Unit u : world.units) {
            if (!selectedUnits.contains(u)) continue;
            double r = Math.max(6, u.def.radius) + 4;
            g.drawOval((int) (u.x - r), (int) (u.y - r), (int) (r * 2), (int) (r * 2));
        }
        g.setStroke(old);
    }

    private void drawAttackRangeCircles(Graphics2D g, World world) {
        if (selectedUnits.size() != 1) return;
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(ATTACK_RANGE_COLOR);
        for (Unit u : selectedUnits) {
            if (u.def.attack == null || !u.def.attack.canAttackAnything()) continue;
            int r = u.def.attack.maxAttackRange;
            if (r <= 0) continue;
            g.drawOval((int) (u.x - r), (int) (u.y - r), r * 2, r * 2);
        }
        g.setStroke(old);
    }

    private void drawPaths(Graphics2D g, World world) {
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(PATH_COLOR);
        for (Unit u : world.units) {
            if (u.faction != Faction.PLAYER || u.path == null || u.path.isEmpty()) continue;
            if (overlayOnlySelected && !selectedUnits.contains(u)) continue;
            double px = u.x;
            double py = u.y;
            for (Point p : u.path) {
                double tx = world.map.cellCenterX(p.x);
                double ty = world.map.cellCenterY(p.y);
                g.drawLine((int) px, (int) py, (int) tx, (int) ty);
                px = tx;
                py = ty;
            }
        }
        g.setStroke(old);
    }

    private void drawIntel(Graphics2D g, World world, Faction viewer) {
        IntelBoard board = world.intelOf(viewer);
        if (board == null) return;
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(2f));
        g.setColor(INTEL_COLOR);
        for (IntelBoard.IntelEntry e : board.knownEnemies()) {
            int sz = 16;
            g.drawRect((int) (e.x - sz / 2), (int) (e.y - sz / 2), sz, sz);
            g.drawString("?", (int) (e.x - 3), (int) (e.y + 4));
        }
        g.setStroke(old);
    }

    private void drawHpBar(Graphics2D g, Unit u) {
        int max = u.def.maxHp;
        if (max <= 0) return;
        double ratio = Math.max(0, Math.min(1.0, (double) u.hp / max));
        int r = (int) Math.max(6, u.def.radius);
        int barW = r * 2 + 4;
        int barH = 3;
        int x = (int) (u.x - barW / 2);
        int y = (int) (u.y - r - 8);
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(x - 1, y - 1, barW + 2, barH + 2);
        g.setColor(new Color(60, 60, 60));
        g.fillRect(x, y, barW, barH);
        // 血条颜色以阵营区分：己方绿色，敌方红色
        Color hpColor = (u.faction == Faction.PLAYER)
                       ? new Color(80, 200, 80)
                       : new Color(220, 60, 60);
        g.setColor(hpColor);
        g.fillRect(x, y, (int) (barW * ratio), barH);
    }

    private void drawShots(Graphics2D g, World world) {
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(1.5f));
        for (CombatSystem.ShotEvent s : world.combatSystem().recentShots) {
            Color c = (s.shooterFaction == Faction.PLAYER)
                ? new Color(120, 200, 255, 220)
                : new Color(255, 180, 120, 220);
            g.setColor(c);
            g.drawLine((int) s.sx, (int) s.sy, (int) s.tx, (int) s.ty);
        }
        g.setStroke(old);
    }

    private static Color colorOf(TerrainType t) {
        switch (t) {
            case DIRT:     return DIRT_COLOR;
            case SAND:     return SAND_COLOR;
            case MOUNTAIN: return MOUNTAIN_COLOR;
            case SHALLOW:  return SHALLOW_COLOR;
            case WATER:    return WATER_COLOR;
            case DEEP:     return DEEP_COLOR;
            case GRASS:
            default:       return GRASS_COLOR;
        }
    }
}
