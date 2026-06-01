package com.gduws.audio;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

/**
 * 背景音乐播放器：复用 RustedWarfare 的 OGG 音乐，借助 JOrbis 纯 Java 解码后经
 * {@link SourceDataLine} 播放
 *
 * <p>音乐按“场景”组织：菜单/布兵、常规战斗、激烈交火。外部只需调用
 * {@link #setScene(Scene)} 切换场景，播放器在后台守护线程内自动循环播放该场景
 * 的曲目，并在曲目播完后随机切换到下一首。切换场景时会打断当前曲目立即换曲。</p>
 *
 * <p>解码或音频设备不可用时静默降级，不影响游戏主流程。</p>
 */
public final class MusicPlayer {

    /** 音乐场景：仅区分目录界面与游戏中 */
    public enum Scene {
        MENU,    // 菜单 / 选关 / 布兵：starting
        BATTLE   // 游戏中：gaming
    }

    private static final int READ_SIZE = 4096;
    private static final int MAX_SAMPLES = 4096;

    private final Map<Scene, List<String>> playlists = new EnumMap<>(Scene.class);
    private final Random random = new Random();

    private volatile Scene currentScene;
    private volatile boolean running = true;
    private volatile boolean enabled = true;
    private volatile float gainDb = -10f;
    private String lastTrack;

    private final Thread worker;

    public MusicPlayer() {
        for (Scene s : Scene.values()) {
            playlists.put(s, new ArrayList<>());
        }
        worker = new Thread(this::runLoop, "GDUWS-Music");
        worker.setDaemon(true);
    }

    /** 从 assets/music 下两个子目录装载曲目，返回是否至少装载到一首 */
    public boolean loadDefault() {
        load(Scene.MENU, "assets/music/starting");
        load(Scene.BATTLE, "assets/music/gaming");
        for (List<String> l : playlists.values()) {
            if (!l.isEmpty()) return true;
        }
        return false;
    }

    private void load(Scene scene, String dir) {
        File d = new File(dir);
        File[] files = d.listFiles((f, name) -> name.toLowerCase().endsWith(".ogg"));
        if (files == null) return;
        List<String> list = playlists.get(scene);
        for (File f : files) {
            list.add(f.getPath());
        }
        Collections.sort(list);
    }

    /** 启动后台播放线程 */
    public void start() {
        if (!worker.isAlive()) {
            worker.start();
        }
    }

    /** 切换音乐场景；与当前相同则忽略，避免重复打断曲目 */
    public void setScene(Scene scene) {
        if (scene == currentScene) return;
        currentScene = scene;
    }

    /** 停止播放（保留线程，可再次 setScene 恢复） */
    public void stop() {
        currentScene = null;
    }

    /** 启用/禁用音乐；禁用时立即停止当前曲目 */
    public void setEnabled(boolean on) {
        enabled = on;
        if (!on) currentScene = null;
    }

    /** 永久关闭播放器并结束后台线程 */
    public void shutdown() {
        running = false;
        currentScene = null;
        worker.interrupt();
    }

    // ------------------------------------------------------------------
    // 后台线程主循环
    // ------------------------------------------------------------------

    private void runLoop() {
        while (running) {
            Scene scene = currentScene;
            if (!enabled || scene == null) {
                sleep(120);
                continue;
            }
            String track = pickTrack(scene);
            if (track == null) {
                sleep(200);
                continue;
            }
            try {
                playFile(track, scene);
            } catch (Exception e) {
                // 解码或设备异常：跳过该曲，稍后重试
                sleep(200);
            }
        }
    }

    /** 从场景曲目池随机取一首，尽量避免与上一首相同 */
    private String pickTrack(Scene scene) {
        List<String> list = playlists.get(scene);
        if (list == null || list.isEmpty()) return null;
        if (list.size() == 1) return list.get(0);
        String track;
        do {
            track = list.get(random.nextInt(list.size()));
        } while (track.equals(lastTrack));
        lastTrack = track;
        return track;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 当前应继续播放：未关闭、未禁用且场景未变 */
    private boolean shouldContinue(Scene scene) {
        return running && enabled && currentScene == scene;
    }

    // ------------------------------------------------------------------
    // JOrbis OGG 解码 + 播放（改写自 JOrbis DecodeExample）
    // ------------------------------------------------------------------

    private void playFile(String path, Scene scene) throws Exception {
        try (InputStream input = new BufferedInputStream(new FileInputStream(path))) {
            decode(input, scene);
        }
    }

    private void decode(InputStream input, Scene scene) throws Exception {
        SyncState oy = new SyncState();
        StreamState os = new StreamState();
        Page og = new Page();
        Packet op = new Packet();
        Info vi = new Info();
        Comment vc = new Comment();
        DspState vd = new DspState();
        Block vb = new Block(vd);

        oy.init();

        // --- 读取 Ogg 第一页，解析 Vorbis 标识头 ---
        int index = oy.buffer(READ_SIZE);
        int bytes = input.read(oy.data, index, READ_SIZE);
        oy.wrote(bytes);

        if (oy.pageout(og) != 1) {
            return; // 非 Ogg 流或文件过短，放弃
        }
        os.init(og.serialno());
        vi.init();
        vc.init();
        if (os.pagein(og) < 0 || os.packetout(op) != 1
                || vi.synthesis_headerin(vc, op) < 0) {
            return; // 不是 Vorbis 音频
        }

        // --- 继续读取剩余两个头包（注释头、配置头）---
        int headers = 1;
        while (headers < 3) {
            int result = oy.pageout(og);
            if (result == 0) {
                index = oy.buffer(READ_SIZE);
                bytes = input.read(oy.data, index, READ_SIZE);
                if (bytes <= 0) return;
                oy.wrote(bytes);
                continue;
            }
            if (result == 1) {
                os.pagein(og);
                while (headers < 3) {
                    result = os.packetout(op);
                    if (result == 0) break;
                    if (result == -1 || vi.synthesis_headerin(vc, op) < 0) {
                        return;
                    }
                    headers++;
                }
            }
        }

        int channels = vi.channels;
        int rate = vi.rate;
        byte[] convbuffer = new byte[MAX_SAMPLES * 2 * channels];

        vd.synthesis_init(vi);
        vb.init(vd);

        SourceDataLine line = openLine(rate, channels);
        if (line == null) return;

        float[][][] pcm = new float[1][][];
        int[] pcmIndex = new int[channels];

        try {
            boolean eos = false;
            while (!eos && shouldContinue(scene)) {
                int result = oy.pageout(og);
                if (result == 0) {
                    // 需要更多数据
                    index = oy.buffer(READ_SIZE);
                    bytes = input.read(oy.data, index, READ_SIZE);
                    if (bytes <= 0) {
                        eos = true;
                    } else {
                        oy.wrote(bytes);
                    }
                    continue;
                }
                if (result == -1) {
                    continue; // 页缺失，跳过
                }
                os.pagein(og);
                while (shouldContinue(scene)) {
                    result = os.packetout(op);
                    if (result == 0) break;
                    if (result == -1) continue;
                    decodePacket(op, vb, vd, line, convbuffer, channels, pcm, pcmIndex, scene);
                }
                if (og.eos() != 0) {
                    eos = true;
                }
            }
            line.drain();
        } finally {
            line.stop();
            line.close();
            os.clear();
            vb.clear();
            vd.clear();
            vi.clear();
            oy.clear();
        }
    }

    /** 解码单个音频包并写入音频线 */
    private void decodePacket(Packet op, Block vb, DspState vd, SourceDataLine line,
                              byte[] convbuffer, int channels, float[][][] pcm, int[] pcmIndex,
                              Scene scene) {
        if (vb.synthesis(op) == 0) {
            vd.synthesis_blockin(vb);
        }
        int samples;
        while ((samples = vd.synthesis_pcmout(pcm, pcmIndex)) > 0 && shouldContinue(scene)) {
            float[][] data = pcm[0];
            int bout = Math.min(samples, MAX_SAMPLES);
            for (int ch = 0; ch < channels; ch++) {
                int ptr = ch * 2;
                int mono = pcmIndex[ch];
                for (int j = 0; j < bout; j++) {
                    int val = (int) (data[ch][mono + j] * 32767.0);
                    if (val > 32767) val = 32767;
                    if (val < -32768) val = -32768;
                    convbuffer[ptr] = (byte) val;            // 低字节
                    convbuffer[ptr + 1] = (byte) (val >>> 8); // 高字节（小端）
                    ptr += 2 * channels;
                }
            }
            line.write(convbuffer, 0, 2 * channels * bout);
            vd.synthesis_read(bout);
        }
    }

    /** 打开与解码参数匹配的音频输出线，失败返回 null */
    private SourceDataLine openLine(int rate, int channels) {
        try {
            AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) return null;
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            applyGain(line);
            line.start();
            return line;
        } catch (Exception e) {
            return null;
        }
    }

    /** 按设定音量调整增益（若设备支持） */
    private void applyGain(SourceDataLine line) {
        try {
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl ctrl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                float g = Math.max(ctrl.getMinimum(), Math.min(ctrl.getMaximum(), gainDb));
                ctrl.setValue(g);
            }
        } catch (Exception ignored) {
            // 不支持音量控制时忽略
        }
    }
}
