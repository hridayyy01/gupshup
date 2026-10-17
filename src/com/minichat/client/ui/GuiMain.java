package com.minichat.client.ui;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class GuiMain {
    private GuiMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            setNimbus();
            new LoginFrame().setVisible(true);
        });
    }

    private static void setNimbus() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception ignored) {
        }
    }
}
