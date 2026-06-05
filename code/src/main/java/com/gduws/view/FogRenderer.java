package com.gduws.view;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

import com.gduws.model.Faction;
import com.gduws.model.GameMap;
import com.gduws.model.Unit;
import com.gduws.model.World;

/**
 * 战争迷雾渲染器：在世界坐标系上叠加一层暗色蒙版
 *
 * <p>两种模式：</p>
 * <ul>
 *   <li>{@link Mode#DEPLOY}：布兵阶段，依据地图可布兵标记，对不可布兵区域覆盖暗色迷雾，并对格边做高斯模糊柔化</li>
 *   <li>{@link Mode#BATTLE}：战斗阶段，以己方全部单位 {@code sightRange} 的并集为可视范围，
 *       范围之外覆盖暗色迷雾，可视边缘以径向渐变柔化，呈现经典 RTS 视野揭示效果</li>
 * </ul>
 *
 * <p>迷雾绘制到一张与地图等大的离屏缓冲，对可视区域用 {@link AlphaComposite#DstOut}
 * “擦除”出柔和的圆形窗口，再整体绘制到战场，避免逐格硬边</p>
 */
public final class FogRenderer {

    public enum Mode { NONE, DEPLOY, BATTLE }

    /** 迷雾基色：偏冷的深蓝黑，带较高不透明度营造“被遮蔽”的厚重感（布兵/战斗统一使用） */
    private static final Color FOG_COLOR = new Color(8, 12, 24, 210);
    /** 边缘羽化半径（像素），用于柔化布兵迷雾的格边 */
    private static final int FEATHER_RADIUS = 8;

    /** 离屏迷雾缓冲，按地图像素尺寸缓存复用 */
    private BufferedImage buffer;
    private int bufW = -1;
    private int bufH = -1;

    /** 布兵迷雾缓存（仅随地图变化），避免每帧重做模糊 */
    private BufferedImage deployCache;
    private GameMap deployCacheMap;
    private int deployCacheW = -1;
    private int deployCacheH = -1;

    /** 绘制迷雾层（已处于世界坐标系，调用方负责相机变换） */
    public void render(Graphics2D g, World world, Mode mode) {
        if (mode == Mode.NONE) return;
        GameMap map = world.map;
        int w = map.pixelWidth();
        int h = map.pixelHeight();
        if (w <= 0 || h <= 0) return;

        if (mode == Mode.DEPLOY) {
            // 布兵迷雾仅取决于地图，构建一次（含边缘柔化）后缓存复用
            g.drawImage(deployFog(map, w, h), 0, 0, null);
            return;
        }

        BufferedImage buf = ensureBuffer(w, h);
        Graphics2D bg = buf.createGraphics();
        try {
            // 清空缓冲为全透明
            bg.setComposite(AlphaComposite.Clear);
            bg.fillRect(0, 0, w, h);
            bg.setComposite(AlphaComposite.SrcOver);
            bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintBattleFog(bg, world, map);
        } finally {
            bg.dispose();
        }
        g.drawImage(buf, 0, 0, null);
    }

    /**
     * 布兵阶段迷雾：对不可布兵区整格铺暗色迷雾，再对整张蒙版做高斯模糊柔化格边。
     * 结果仅取决于地图，按地图缓存复用，避免每帧重算模糊。
     */
    private BufferedImage deployFog(GameMap map, int w, int h) {
        if (deployCache != null && deployCacheMap == map
                && deployCacheW == w && deployCacheH == h) {
            return deployCache;
        }
        int ts = map.tileSize;
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D mg = mask.createGraphics();
        mg.setColor(FOG_COLOR);
        for (int r = 0; r < map.rows; r++) {
            for (int c = 0; c < map.cols; c++) {
                if (map.isDeployForbidden(c, r)) {
                    mg.fillRect(c * ts, r * ts, ts, ts);
                }
            }
        }
        mg.dispose();

        BufferedImage soft = blur(mask, FEATHER_RADIUS);
        deployCache = soft;
        deployCacheMap = map;
        deployCacheW = w;
        deployCacheH = h;
        return soft;
    }

    /** 对迷雾蒙版做高斯模糊，使硬质格边过渡为柔和羽化边 */
    private static BufferedImage blur(BufferedImage src, int radius) {
        if (radius <= 0) return src;
        ConvolveOp op = new ConvolveOp(gaussianKernel(radius), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage dst = new BufferedImage(
            src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        op.filter(src, dst);
        return dst;
    }

    /** 构建归一化的二维高斯卷积核 */
    private static Kernel gaussianKernel(int radius) {
        int size = radius * 2 + 1;
        float[] data = new float[size * size];
        double sigma = Math.max(1.0, radius / 2.0);
        double twoSigma2 = 2 * sigma * sigma;
        double sum = 0;
        int i = 0;
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                double v = Math.exp(-(x * x + y * y) / twoSigma2);
                data[i++] = (float) v;
                sum += v;
            }
        }
        for (int j = 0; j < data.length; j++) {
            data[j] /= (float) sum;
        }
        return new Kernel(size, size, data);
    }

    /** 战斗阶段：先铺满迷雾，再以己方单位视野“擦”出柔和的可视窗口 */
    private void paintBattleFog(Graphics2D bg, World world, GameMap map) {
        bg.setColor(FOG_COLOR);
        bg.fillRect(0, 0, map.pixelWidth(), map.pixelHeight());

        // 用 DstOut 把可视圆从迷雾里擦掉：圆心全透明、边缘保留迷雾，形成柔和过渡
        Composite old = bg.getComposite();
        bg.setComposite(AlphaComposite.DstOut);
        float[] fractions = { 0f, 0.7f, 1f };
        // alpha 渐变：中心完全擦除 → 接近边缘逐渐减弱擦除 → 边缘不擦除
        Color[] colors = {
            new Color(0, 0, 0, 255),
            new Color(0, 0, 0, 230),
            new Color(0, 0, 0, 0)
        };
        for (Unit u : world.units) {
            if (u.faction != Faction.PLAYER || u.isDead()) continue;
            int sight = u.def.sightRange;
            if (sight <= 0) continue;
            float radius = sight;
            Point2D center = new Point2D.Double(u.x, u.y);
            RadialGradientPaint paint =
                new RadialGradientPaint(center, radius, fractions, colors);
            bg.setPaint(paint);
            bg.fillOval((int) (u.x - radius), (int) (u.y - radius),
                        (int) (radius * 2), (int) (radius * 2));
        }
        bg.setComposite(old);
    }

    private BufferedImage ensureBuffer(int w, int h) {
        if (buffer == null || bufW != w || bufH != h) {
            buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            bufW = w;
            bufH = h;
        }
        return buffer;
    }
}
