package com.gduws.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import com.gduws.model.Faction;
import com.gduws.model.GameMap;
import com.gduws.model.MovementType;
import com.gduws.model.TerrainType;
import com.gduws.model.Unit;
import com.gduws.model.World;

/** 渲染战场：地形网格 + 单位（M1 阶段用色块/圆形占位）。 */
public class GameRenderer {

    private static final Color PLAIN_COLOR    = new Color(96, 152, 84);
    private static final Color MOUNTAIN_COLOR = new Color(120, 110, 100);
    private static final Color WATER_COLOR    = new Color(58, 110, 176);
    private static final Color GRID_COLOR     = new Color(0, 0, 0, 40);

    private static final Color PLAYER_COLOR = new Color(70, 130, 220);
    private static final Color ENEMY_COLOR  = new Color(210, 70, 70);

    public void render(Graphics2D g, World world) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawTerrain(g, world.map);
        for (Unit u : world.units) {
            drawUnit(g, u);
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

        g.setColor(base);
        g.fillOval(x, y, d, d);
        g.setColor(base.darker());
        g.setStroke(new BasicStroke(2f));
        g.drawOval(x, y, d, d);

        // 层标记字母：L/W/A/U
        g.setColor(Color.WHITE);
        String mark = layerMark(u.def.movementType);
        g.drawString(mark, (int) (u.x - 4), (int) (u.y + 4));
    }

    private static String layerMark(MovementType mt) {
        switch (mt) {
            case LAND:       return "L";
            case WATER:      return "W";
            case AIR:        return "A";
            case UNDERWATER: return "U";
            default:         return "?";
        }
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
