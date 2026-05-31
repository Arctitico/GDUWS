package com.gduws.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;

import com.gduws.model.CombatSystem;
import com.gduws.model.Faction;
import com.gduws.model.GameMap;
import com.gduws.model.IntelBoard;
import com.gduws.model.MovementType;
import com.gduws.model.TerrainType;
import com.gduws.model.Unit;
import com.gduws.model.World;

/** 渲染战场：地形网格 + 单位 + 调试覆盖层（视野/路径/已知敌情）。 */
public class GameRenderer {

    private static final Color PLAIN_COLOR    = new Color(96, 152, 84);
    private static final Color MOUNTAIN_COLOR = new Color(120, 110, 100);
    private static final Color WATER_COLOR    = new Color(58, 110, 176);
    private static final Color GRID_COLOR     = new Color(0, 0, 0, 40);

    private static final Color PLAYER_COLOR = new Color(70, 130, 220);
    private static final Color ENEMY_COLOR  = new Color(210, 70, 70);

    private static final Color PATH_COLOR   = new Color(255, 255, 255, 160);
    private static final Color SIGHT_COLOR  = new Color(255, 255, 255, 35);
    private static final Color INTEL_COLOR  = new Color(255, 200, 0, 220);

    /** 是否绘制玩家方调试覆盖层（视野圈、路径、已知敌情）。 */
    public boolean showOverlay = true;
    /** 当前选中的单位（用于高亮）。 */
    public Unit selectedUnit;

    private final SpriteCache sprites = new SpriteCache();

    public void render(Graphics2D g, World world) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawTerrain(g, world.map);

        if (showOverlay) {
            drawVisionCircles(g, world);
            drawPaths(g, world);
        }

        drawShots(g, world);

        for (Unit u : world.units) {
            drawUnit(g, u);
        }
        for (Unit u : world.units) {
            drawHpBar(g, u);
        }

        if (showOverlay) {
            drawIntel(g, world, Faction.PLAYER);
        }
    }


    private void drawTerrain(Graphics2D g, GameMap map) {
        int ts = map.tileSize;
        for (int r = 0; r < map.rows; r++) {
            for (int c = 0; c < map.cols; c++) {
                TerrainType t = map.tileAt(c, r).terrain;
                g.setColor(colorOf(t));
                g.fillRect(c * ts, r * ts, ts, ts);
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
    }

    private void drawVisionCircles(Graphics2D g, World world) {
        g.setColor(SIGHT_COLOR);
        for (Unit u : world.units) {
            if (u.faction != Faction.PLAYER) continue;
            int s = u.def.sightRange;
            if (s <= 0) continue;
            g.fillOval((int) (u.x - s), (int) (u.y - s), s * 2, s * 2);
        }
    }

    private void drawPaths(Graphics2D g, World world) {
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(PATH_COLOR);
        for (Unit u : world.units) {
            if (u.faction != Faction.PLAYER || u.path == null || u.path.isEmpty()) continue;
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
        Color hpColor = ratio > 0.5 ? new Color(80, 200, 80)
                       : ratio > 0.25 ? new Color(230, 200, 60)
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
            case MOUNTAIN: return MOUNTAIN_COLOR;
            case WATER:    return WATER_COLOR;
            case PLAIN:
            default:       return PLAIN_COLOR;
        }
    }
}
