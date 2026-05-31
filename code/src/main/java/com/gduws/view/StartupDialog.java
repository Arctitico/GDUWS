package com.gduws.view;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.EmptyBorder;

/**
 * 启动选择窗口：在进入游戏前让玩家选择全屏或窗口（含分辨率）模式
 */
public final class StartupDialog {

    /** 显示模式配置：全屏标志 + 窗口分辨率 */
    public static final class Config {
        public final boolean fullscreen;
        public final int width;
        public final int height;

        public Config(boolean fullscreen, int width, int height) {
            this.fullscreen = fullscreen;
            this.width = width;
            this.height = height;
        }
    }

    private StartupDialog() {
    }

    /**
     * 弹出模态选择窗口，返回玩家选择的显示配置
     *
     * @return 选定的配置；若玩家关闭窗口则返回 {@code null}
     */
    public static Config choose() {
        final Config[] result = new Config[1];

        JDialog dialog = new JDialog((java.awt.Frame) null, "GDUWS — 显示设置", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel title = new JLabel("选择游玩方式");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        root.add(title);
        root.add(Box.createVerticalStrut(12));

        ButtonGroup group = new ButtonGroup();

        JRadioButton full = new JRadioButton("全屏 (Full screen)");
        full.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        group.add(full);
        root.add(full);

        // 窗口分辨率选项
        int[][] windowSizes = {
            {1280, 720},
            {1600, 900},
            {1920, 1080},
        };
        JRadioButton[] winButtons = new JRadioButton[windowSizes.length];
        for (int i = 0; i < windowSizes.length; i++) {
            int w = windowSizes[i][0];
            int h = windowSizes[i][1];
            JRadioButton b = new JRadioButton("窗口 (Window)  " + w + " × " + h);
            b.setAlignmentX(JPanel.LEFT_ALIGNMENT);
            group.add(b);
            winButtons[i] = b;
            root.add(b);
        }

        // 默认选中第一个能放进当前屏幕的窗口分辨率，否则选全屏
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getMaximumWindowBounds();
        int defaultIndex = -1;
        for (int i = windowSizes.length - 1; i >= 0; i--) {
            if (windowSizes[i][0] <= screen.width && windowSizes[i][1] <= screen.height) {
                defaultIndex = i;
            }
        }
        if (defaultIndex >= 0) {
            winButtons[defaultIndex].setSelected(true);
        } else {
            full.setSelected(true);
        }

        root.add(Box.createVerticalStrut(16));

        JButton ok = new JButton("开始游戏");
        ok.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        ok.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        ok.addActionListener(e -> {
            if (full.isSelected()) {
                result[0] = new Config(true, 0, 0);
            } else {
                for (int i = 0; i < winButtons.length; i++) {
                    if (winButtons[i].isSelected()) {
                        result[0] = new Config(false, windowSizes[i][0], windowSizes[i][1]);
                        break;
                    }
                }
            }
            dialog.dispose();
        });
        root.add(ok);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);

        return result[0];
    }
}
