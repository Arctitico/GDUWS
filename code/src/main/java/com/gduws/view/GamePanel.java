package com.gduws.view;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JPanel;

import com.gduws.model.World;

/** 战场画布：使用 {@link GameRenderer} 绘制世界。 */
public class GamePanel extends JPanel {

    private final World world;
    private final GameRenderer renderer = new GameRenderer();

    public GamePanel(World world) {
        this.world = world;
        setPreferredSize(new Dimension(world.map.pixelWidth(), world.map.pixelHeight()));
    }

    public GameRenderer renderer() {
        return renderer;
    }

    public World world() {
        return world;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        renderer.render((Graphics2D) g, world);
    }
}
