package com.gduws.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;

import com.gduws.model.World;

/**
 * 战场画布：使用 {@link GameRenderer} 绘制世界，并提供以鼠标滚轮控制的缩放视口。
 *
 * <p>缩放只作用于地图，不影响右侧 UI（侧栏是独立面板）。地图之外的区域绘制为黑色。</p>
 */
public class GamePanel extends JPanel {

    private final World world;
    private final GameRenderer renderer = new GameRenderer();

    /** 缩放系数：>1 放大，<1 缩小 */
    private double scale = 1.0;
    /** 世界原点在屏幕上的像素偏移（应用缩放后） */
    private double offsetX = 0;
    private double offsetY = 0;
    /** 相机是否已根据面板尺寸完成初始化 */
    private boolean cameraInit = false;

    /** 右键拖动平移的状态：上次光标位置与本次按下是否发生过拖动 */
    private int lastDragX;
    private int lastDragY;
    private boolean rightDragMoved = false;

    public GamePanel(World world) {
        this.world = world;
        setPreferredSize(new Dimension(world.map.pixelWidth(), world.map.pixelHeight()));
        // 地图之外的区域为黑色
        setBackground(Color.BLACK);
        setOpaque(true);
        addMouseWheelListener(this::onWheel);
        installPanHandler();
    }

    /** 安装右键拖动平移地图的处理器 */
    private void installPanHandler() {
        MouseInputAdapter pan = new MouseInputAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    ensureCamera();
                    lastDragX = e.getX();
                    lastDragY = e.getY();
                    rightDragMoved = false;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!SwingUtilities.isRightMouseButton(e)) {
                    return;
                }
                offsetX += e.getX() - lastDragX;
                offsetY += e.getY() - lastDragY;
                lastDragX = e.getX();
                lastDragY = e.getY();
                rightDragMoved = true;
                clampOffset();
                repaint();
            }
        };
        addMouseListener(pan);
        addMouseMotionListener(pan);
    }

    /** 本次右键操作是否为拖动（用于让点击逻辑忽略拖动结束的释放） */
    public boolean rightDragMoved() {
        return rightDragMoved;
    }

    public GameRenderer renderer() {
        return renderer;
    }

    public World world() {
        return world;
    }

    /** 让整张地图恰好铺满面板所需的缩放系数 */
    private double fitScale() {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return 1.0;
        }
        double sx = (double) getWidth() / world.map.pixelWidth();
        double sy = (double) getHeight() / world.map.pixelHeight();
        return Math.min(sx, sy);
    }

    /** 最小缩放：不允许把地图缩得过小（在贴合基础上再留少量黑边） */
    private double minScale() {
        return fitScale() * 0.6;
    }

    /** 最大缩放：可放大查看地图局部 */
    private double maxScale() {
        return Math.max(fitScale() * 8.0, 4.0);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** 首次拿到有效尺寸时，以贴合缩放居中显示整张地图 */
    private void ensureCamera() {
        if (!cameraInit && getWidth() > 0 && getHeight() > 0) {
            scale = fitScale();
            clampOffset();
            cameraInit = true;
        }
    }

    private void onWheel(MouseWheelEvent e) {
        ensureCamera();
        double oldScale = scale;
        double factor = (e.getWheelRotation() < 0) ? 1.1 : 1.0 / 1.1;
        double newScale = clamp(scale * factor, minScale(), maxScale());
        if (newScale == oldScale) {
            return;
        }
        // 以光标所指世界点为锚点缩放，保持其屏幕位置不变
        double worldX = (e.getX() - offsetX) / oldScale;
        double worldY = (e.getY() - offsetY) / oldScale;
        scale = newScale;
        offsetX = e.getX() - worldX * scale;
        offsetY = e.getY() - worldY * scale;
        clampOffset();
        repaint();
    }

    /** 约束偏移：地图小于视口时居中，大于视口时不让地图边界缩进视口内 */
    private void clampOffset() {
        double mapW = world.map.pixelWidth() * scale;
        double mapH = world.map.pixelHeight() * scale;
        double pw = getWidth();
        double ph = getHeight();
        if (mapW <= pw) {
            offsetX = (pw - mapW) / 2.0;
        } else {
            offsetX = clamp(offsetX, pw - mapW, 0);
        }
        if (mapH <= ph) {
            offsetY = (ph - mapH) / 2.0;
        } else {
            offsetY = clamp(offsetY, ph - mapH, 0);
        }
    }

    /** 屏幕 X 像素 → 世界 X 像素 */
    public double worldX(int screenX) {
        return (screenX - offsetX) / scale;
    }

    /** 屏幕 Y 像素 → 世界 Y 像素 */
    public double worldY(int screenY) {
        return (screenY - offsetY) / scale;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        ensureCamera();
        // 面板尺寸变化后重新约束缩放与偏移
        scale = clamp(scale, minScale(), maxScale());
        clampOffset();

        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(offsetX, offsetY);
        g2.scale(scale, scale);
        renderer.render(g2, world);
        g2.dispose();
    }
}
