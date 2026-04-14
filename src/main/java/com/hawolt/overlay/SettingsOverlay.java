package com.hawolt.overlay;

import com.hawolt.Settings;
import com.hawolt.logger.Logger;
import com.hawolt.platform.Platform;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;
import java.util.function.Consumer;

public class SettingsOverlay {

    private final Consumer<Boolean> onSettingsOpenChanged;
    private final SettingsPanel panel;
    private final Platform platform;
    private final JDialog dialog;
    private final Robot robot;

    private boolean gameRunning = false;
    private boolean gameIngame = false;
    private boolean shown = false;

    public SettingsOverlay(
            Platform platform,
            Settings settings,
            Runnable onSave,
            Runnable onClose,
            Runnable onForceShowOverlay,
            Runnable onForceHideOverlay,
            Runnable onRecalibrate,
            Consumer<Boolean> onSettingsOpenChanged,
            Runnable onSuspendHotkeys,
            Runnable onResumeHotkeys,
            Consumer<String> onPobLoad
    ) {
        this.platform = platform;
        this.robot = createRobot();
        this.onSettingsOpenChanged = onSettingsOpenChanged;

        Logger.info("[SettingsOverlay] Initializing dialog");

        dialog = new JDialog((Frame) null, false);
        dialog.setUndecorated(true);
        dialog.setBackground(new Color(0, 0, 0, 0));
        dialog.setFocusableWindowState(true);

        panel = new SettingsPanel(
                settings,
                onSave,
                onClose,
                onForceShowOverlay,
                onForceHideOverlay,
                onRecalibrate,
                this::requestKeyFocus,
                onSuspendHotkeys,
                onResumeHotkeys,
                onPobLoad
        );

        dialog.setContentPane(panel);
        dialog.pack();
        Logger.info("[SettingsOverlay] Dialog packed, size={}", dialog.getSize());

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent windowEvent) {
                Logger.info("[SettingsOverlay] windowOpened - clearing WS_EX_NOACTIVATE and requesting activation");
                platform.makeInteractable(dialog);
                dialog.toFront();
                dialog.requestFocus();
            }

            @Override
            public void windowActivated(WindowEvent windowEvent) {
                Logger.info("[SettingsOverlay] windowActivated - requesting focus on panel");
                panel.requestFocusInWindow();
            }

            @Override
            public void windowDeactivated(WindowEvent windowEvent) {
                Logger.info("[SettingsOverlay] windowDeactivated");
            }

            @Override
            public void windowClosed(WindowEvent windowEvent) {
                Logger.info("[SettingsOverlay] windowClosed");
            }

            @Override
            public void windowClosing(WindowEvent windowEvent) {
                Logger.info("[SettingsOverlay] windowClosing");
            }
        });

        Logger.info("[SettingsOverlay] Init complete. robot={}", robot != null ? "ok" : "null (AWTException)");
    }

    public void setBandit(String bandit) {
        panel.setBandit(bandit);
    }

    public JSONObject getLoadouts() {
        return panel.getLoadouts();
    }

    public String getSelectedLoadout() {
        return panel.getSelectedLoadout();
    }

    public void setLoadouts(JSONObject loadouts, String savedLoadout) {
        panel.setLoadouts(loadouts, savedLoadout);
        dialog.setSize(panel.getPreferredSize());
    }

    public void onGameRunningChanged(boolean running) {
        Logger.info("[SettingsOverlay] onGameRunningChanged: {} -> {}", gameRunning, running);
        gameRunning = running;
        if (!running) {
            Logger.info("[SettingsOverlay] Game stopped - hiding settings");
            SwingUtilities.invokeLater(this::hide);
        }
    }

    public void onIngameChanged(boolean ingame) {
        Logger.info("[SettingsOverlay] onIngameChanged: {} -> {}", gameIngame, ingame);
        gameIngame = ingame;
    }

    public void toggle() {
        Logger.info(
                "[SettingsOverlay] toggle() called - scheduling on EDT (shown={} gameRunning={})",
                shown,
                gameRunning
        );
        SwingUtilities.invokeLater(() -> {
            Logger.info(
                    "[SettingsOverlay] toggle() on EDT - shown={} gameRunning={}",
                    shown,
                    gameRunning
            );
            if (!gameRunning) {
                Logger.warn("[SettingsOverlay] toggle() blocked - gameRunning=false. Is the game process running?");
                return;
            }
            if (shown) {
                Logger.info("[SettingsOverlay] toggle() -> hiding");
                hide();
            } else {
                Logger.info("[SettingsOverlay] toggle() -> showing");
                show();
            }
        });
    }

    public void hide() {
        Logger.info("[SettingsOverlay] hide() called - shown={} gameIngame={}", shown, gameIngame);
        dialog.setVisible(false);
        shown = false;
        onSettingsOpenChanged.accept(false);
        if (gameIngame) {
            Logger.info("[SettingsOverlay] hide() pressing ESC (ingame=true)");
            pressEscape();
        }
        Logger.info("[SettingsOverlay] hide() complete");
    }

    private void show() {
        Logger.info("[SettingsOverlay] show() called - gameIngame={}", gameIngame);
        if (gameIngame) {
            Logger.info("[SettingsOverlay] show() pressing ESC before opening (ingame=true)");
            pressEscape();
        }
        centerOnGameScreen();
        Logger.info(
                "[SettingsOverlay] show() setting dialog visible at location={} size={}",
                dialog.getLocation(),
                dialog.getSize()
        );
        dialog.setVisible(true);
        shown = true;
        onSettingsOpenChanged.accept(true);
        Logger.info("[SettingsOverlay] show() complete");
    }

    private void pressEscape() {
        if (robot == null) {
            Logger.warn("[SettingsOverlay] pressEscape() skipped - robot is null");
            return;
        }
        Logger.info("[SettingsOverlay] pressEscape()");
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
    }

    private void requestKeyFocus() {
        Logger.info("[SettingsOverlay] requestKeyFocus()");
        dialog.toFront();
        dialog.requestFocus();
        panel.requestFocusInWindow();
    }

    private void centerOnGameScreen() {
        Optional<Rectangle> gameBounds = platform.getGameWindowBounds();
        Logger.info("[SettingsOverlay] centerOnGameScreen() - gameBoundsFound={}", gameBounds.isPresent());
        Rectangle screen = gameBounds.orElse(
                new Rectangle(
                        0,
                        0,
                        Toolkit.getDefaultToolkit().getScreenSize().width,
                        Toolkit.getDefaultToolkit().getScreenSize().height
                )
        );
        Dimension panelSize = panel.getPreferredSize();
        int centerX = screen.x + (screen.width - panelSize.width) / 2;
        int centerY = screen.y + (screen.height - panelSize.height) / 2;
        Logger.info(
                "[SettingsOverlay] centerOnGameScreen() - screen={} panelSize={} -> location=({},{})",
                screen,
                panelSize,
                centerX,
                centerY
        );
        dialog.setLocation(centerX, centerY);
        dialog.setSize(panelSize);
    }

    private Robot createRobot() {
        try {
            Robot r = new Robot();
            Logger.info("[SettingsOverlay] Robot created successfully");
            return r;
        } catch (AWTException exception) {
            Logger.warn("[SettingsOverlay] Robot creation failed: {}", exception.getMessage());
            return null;
        }
    }
}