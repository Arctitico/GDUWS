package com.gduws;

import javax.swing.SwingUtilities;

import com.gduws.view.GameFrame;
import com.gduws.view.StartupDialog;

/**
 * GDUWS 程序入口（Ghost Domains: Unmanned Warfare Sim）。
 *
 * <p>M1 阶段：自动加载 level_01，进入布兵阶段，玩家放置己方单位后点击开始进入战斗态。</p>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StartupDialog.Config config = StartupDialog.choose();
            if (config == null) {
                System.exit(0);
                return;
            }
            GameFrame frame = new GameFrame(config);
            frame.setVisible(true);
        });
    }
}
