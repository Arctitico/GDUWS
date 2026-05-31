package com.gduws.view;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * 加载并缓存单位 PNG 素材（复用 RustedWarfare），按朝向旋转绘制
 * RustedWarfare 单位贴图默认朝上（北），故绘制时额外旋转 +PI/2 对齐 facing
 */
public final class SpriteCache {

    private final Map<String, BufferedImage> cache = new HashMap<>();
    private static final Object MISSING = new Object();
    private final Map<String, Object> missing = new HashMap<>();

    /** 取得指定路径的贴图，加载失败返回 null（缓存失败结果避免反复尝试） */
    public BufferedImage get(String path) {
        if (path == null || path.isEmpty()) return null;
        BufferedImage img = cache.get(path);
        if (img != null) return img;
        if (missing.get(path) == MISSING) return null;
        try {
            File f = new File(path);
            if (f.exists()) {
                img = ImageIO.read(f);
            }
        } catch (Exception e) {
            img = null;
        }
        if (img == null) {
            missing.put(path, MISSING);
            return null;
        }
        cache.put(path, img);
        return img;
    }

    /**
     * 以 (cx,cy) 为中心、按 facing 朝向绘制贴图，保持原始像素尺寸（不缩放）
     * @return 是否成功绘制
     */
    public boolean draw(Graphics2D g, String path, double cx, double cy, double facing) {
        BufferedImage img = get(path);
        if (img == null) return false;

        int iw = img.getWidth();
        int ih = img.getHeight();

        AffineTransform old = g.getTransform();
        AffineTransform tx = new AffineTransform();
        tx.translate(cx, cy);
        tx.rotate(facing + Math.PI / 2);   // 贴图朝上 -> 对齐 facing(0=东)
        tx.translate(-iw / 2.0, -ih / 2.0);
        g.drawImage(img, tx, null);
        g.setTransform(old);
        return true;
    }
}
