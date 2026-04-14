package com.hawolt.overlay;

import com.hawolt.Config;
import com.hawolt.data.GemRequirements;
import com.hawolt.guide.ActTimer;
import com.hawolt.guide.GuidePanel;
import com.hawolt.logger.Logger;
import com.hawolt.platform.Platform;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public class OverlayFrame {

    private final Deque<String> zoneHistory = new ArrayDeque<>();
    private final Point lastDragPoint = new Point();
    private final GuidePanel guidePanel;
    private final JWindow overlayWindow;
    private final Platform platform;
    private final JFrame ownerFrame;

    private boolean hasBeenInGameOnce = false;
    private boolean campaignComplete = false;
    private boolean gameRunning = false;
    private boolean gameFocused = false;
    private boolean settingsOpen = false;
    private boolean forceVisible = false;
    private boolean forceHidden = false;
    private boolean gameIngame = false;
    private String lastZoneName = "";
    private int lastAreaLevel = 0;

    public OverlayFrame(ActTimer actTimer, Platform platform) {
        this.platform = platform;

        ownerFrame = new JFrame();
        ownerFrame.setUndecorated(true);
        ownerFrame.setSize(1, 1);
        ownerFrame.setLocation(-9999, -9999);
        ownerFrame.setFocusableWindowState(false);
        ownerFrame.setType(Window.Type.UTILITY);
        ownerFrame.setOpacity(0f);
        ownerFrame.setVisible(true);

        guidePanel = new GuidePanel();
        guidePanel.setActTimer(actTimer);
        guidePanel.setOnSizeNeeded(this::resizeToSize, this::resizeToContent);

        overlayWindow = buildWindow();

        overlayWindow.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent componentEvent) {
                if (!(gameFocused || settingsOpen) || !shouldBeVisible()) {
                    Logger.warn(
                            "[Overlay] componentShown fired but conditions not met - hiding. gameFocused={} settingsOpen={} shouldBeVisible={}",
                            gameFocused,
                            settingsOpen,
                            shouldBeVisible()
                    );
                    overlayWindow.setVisible(false);
                }
            }
        });

        Logger.info("[Overlay] OverlayFrame initialized");
    }

    public void next() {
        guidePanel.next();
    }

    public void prev() {
        guidePanel.prev();
    }

    public void onCampaignComplete() {
        assertEdt();
        Logger.info("[Overlay] Campaign complete");
        campaignComplete = true;
        updateVisibility();
    }

    public void onCampaignReset() {
        assertEdt();
        Logger.info("[Overlay] Campaign reset");
        campaignComplete = false;
        lastZoneName = "";
        zoneHistory.clear();
    }

    public void setBandit(String bandit) {
        guidePanel.setBandit(bandit);
    }

    public void setLoadouts(JSONObject loadouts, String selectedLoadout) {
        Logger.info("[Overlay] setLoadouts: keys={} selected={}", loadouts.keySet(), selectedLoadout);
        JSONObject skillSet = loadouts.optJSONObject(selectedLoadout);
        if (skillSet == null && !loadouts.isEmpty()) {
            String first = loadouts.keys().next();
            skillSet = loadouts.getJSONObject(first);
            Logger.info("[Overlay] setLoadouts: selected loadout not found, falling back to first: {}", first);
        }
        if (skillSet != null) {
            GemRequirements requirements = GemRequirements.fromSkillSet(skillSet);
            guidePanel.setGemRequirements(requirements);
        }
    }

    public void setActiveClass(String className) {
        Logger.info("[Overlay] Active class set to: {}", className);
        guidePanel.setActiveClass(className);
    }

    public void setGemRequirements(GemRequirements requirements) {
        Logger.info("[Overlay] Gem requirements updated");
        guidePanel.setGemRequirements(requirements);
    }

    public void seekToZone(String zoneName, int areaLevel) {
        if (!gameIngame) {
            Logger.warn("[Overlay] seekToZone({}) ignored - gameIngame=false", zoneName);
            return;
        }
        if (campaignComplete) {
            Logger.warn("[Overlay] seekToZone({}) ignored - campaignComplete=true", zoneName);
            return;
        }
        boolean comingFromHideout = lastZoneName.toLowerCase().contains("hideout");
        boolean enteringHideout = zoneName.toLowerCase().contains("hideout");
        Logger.info(
                "[Overlay] seekToZone: zone={} level={} lastZone={} comingFromHideout={} enteringHideout={}",
                zoneName,
                areaLevel,
                lastZoneName,
                comingFromHideout,
                enteringHideout
        );
        lastZoneName = zoneName;
        lastAreaLevel = areaLevel;
        if (comingFromHideout || enteringHideout) {
            Logger.info("[Overlay] seekToZone: skipping guide seek (hideout transition)");
            return;
        }
        String previousZone = zoneHistory.peekLast();
        zoneHistory.addLast(zoneName);
        Logger.info(
                "[Overlay] seekToZone: forwarding to GuidePanel zone={} previousZone={} historySize={}",
                zoneName,
                previousZone,
                zoneHistory.size()
        );
        guidePanel.seekToZone(zoneName, areaLevel);
    }

    public void seekToZoneFromStart(String zoneName, int areaLevel) {
        if (!gameIngame) {
            Logger.warn("[Overlay] seekToZoneFromStart({}) ignored - gameIngame=false", zoneName);
            return;
        }
        if (campaignComplete) {
            Logger.warn("[Overlay] seekToZoneFromStart({}) ignored - campaignComplete=true", zoneName);
            return;
        }
        boolean enteringHideout = zoneName.toLowerCase().contains("hideout");
        Logger.info(
                "[Overlay] seekToZoneFromStart: zone={} level={} enteringHideout={}",
                zoneName,
                areaLevel,
                enteringHideout
        );
        lastZoneName = zoneName;
        lastAreaLevel = areaLevel;
        if (enteringHideout) {
            Logger.info("[Overlay] seekToZoneFromStart: skipping (hideout)");
            return;
        }
        String previousZone = zoneHistory.peekLast();
        Logger.info(
                "[Overlay] seekToZoneFromStart: forwarding to GuidePanel zone={} previousZone={}",
                zoneName,
                previousZone
        );
        guidePanel.seekToZoneFromStart(zoneName, areaLevel);
    }

    public void toggleMoveMode() {
        boolean entering = !guidePanel.isMoveMode();
        Logger.info("[Overlay] toggleMoveMode: entering={}", entering);
        guidePanel.setMoveMode(entering);
        if (entering) platform.makeInteractable(overlayWindow);
        else platform.makeClickThrough(overlayWindow);
    }

    public void toggleTimerOverlay() {
        Logger.info("[Overlay] toggleTimerOverlay");
        guidePanel.toggleTimerVisible();
    }

    public void reloadGuide() {
        Logger.info("[Overlay] reloadGuide: lastZoneName={}, lastAreaLevel={}", lastZoneName, lastAreaLevel);
        guidePanel.reloadSteps();
        if (!lastZoneName.isEmpty()) {
            String previousZone = zoneHistory.peekLast();
            Logger.info(
                    "[Overlay] reloadGuide: seeking to lastZoneName={} previousZone={}, lastAreaLevel={}",
                    lastZoneName,
                    previousZone,
                    lastAreaLevel
            );
            guidePanel.seekToZoneFromStart(lastZoneName, lastAreaLevel);
        }
    }

    public void onGameRunningChanged(boolean running) {
        assertEdt();
        Logger.info("[Overlay] onGameRunningChanged: {} -> {}", gameRunning, running);
        gameRunning = running;
        if (!running) {
            gameFocused = false;
            gameIngame = false;
            hasBeenInGameOnce = false;
        }
        updateVisibility();
    }

    public void onGameFocusChanged(boolean focused) {
        assertEdt();
        Logger.info("[Overlay] onGameFocusChanged: {} -> {}", gameFocused, focused);
        gameFocused = focused;
        updateVisibility();
    }

    public void onIngameChanged(boolean ingame) {
        assertEdt();
        Logger.info("[Overlay] onIngameChanged: {} -> {} (campaignComplete={})", gameIngame, ingame, campaignComplete);
        if (ingame && !campaignComplete) hasBeenInGameOnce = true;
        gameIngame = ingame;
        updateVisibility();
    }

    public void onSettingsOpenChanged(boolean open) {
        assertEdt();
        Logger.info("[Overlay] onSettingsOpenChanged: {}", open);
        settingsOpen = open;
        updateVisibility();
    }

    public void forceShow() {
        assertEdt();
        Logger.info("[Overlay] forceShow");
        forceVisible = true;
        forceHidden = false;
        updateVisibility();
    }

    public void forceHide() {
        assertEdt();
        Logger.info("[Overlay] forceHide");
        forceHidden = true;
        forceVisible = false;
        updateVisibility();
    }

    public void toggleTimerPause() {
        guidePanel.toggleTimerPause();
    }

    private boolean shouldBeVisible() {
        if (!gameRunning) return false;
        if (forceHidden) return false;
        if (campaignComplete) return false;
        if (forceVisible) return true;
        return gameIngame || hasBeenInGameOnce;
    }

    private void updateVisibility() {
        boolean shouldBe = shouldBeVisible();
        boolean visible = (gameFocused || settingsOpen) && shouldBe;
        Logger.info(
                "[Overlay] updateVisibility: gameFocused={} settingsOpen={} shouldBeVisible={} -> visible={} (gameRunning={} forceVisible={} forceHidden={} campaignComplete={} gameIngame={} hasBeenInGameOnce={})",
                gameFocused,
                settingsOpen,
                shouldBe,
                visible,
                gameRunning,
                forceVisible,
                forceHidden,
                campaignComplete,
                gameIngame,
                hasBeenInGameOnce
        );
        overlayWindow.setAlwaysOnTop(visible);
        overlayWindow.setVisible(visible);
        if (!visible && guidePanel.isMoveMode()) {
            Logger.info("[Overlay] updateVisibility: hiding overlay, disabling move mode");
            guidePanel.setMoveMode(false);
            platform.makeClickThrough(overlayWindow);
        }
    }

    private JWindow buildWindow() {
        JWindow window = new JWindow(ownerFrame);
        window.setBackground(new Color(0, 0, 0, 0));
        window.setFocusableWindowState(false);
        window.setType(Window.Type.UTILITY);
        window.setContentPane(guidePanel);
        window.pack();
        window.setLocation(defaultPosition());
        platform.makeClickThrough(window);
        attachDragListener();
        return window;
    }

    private void resizeToSize(Dimension size) {
        overlayWindow.setSize(size);
    }

    private void resizeToContent() {
        overlayWindow.setSize(guidePanel.computeRequiredSize());
    }

    private Point defaultPosition() {
        Optional<Rectangle> gameBounds = platform.getGameWindowBounds();
        Rectangle screenBounds = gameBounds.orElse(
                new Rectangle(
                        GraphicsEnvironment
                                .getLocalGraphicsEnvironment()
                                .getDefaultScreenDevice()
                                .getDefaultConfiguration()
                                .getBounds()
                )
        );
        int x = screenBounds.x + (int) Math.round(
                Config.DEFAULT_X_AT_1440P * (screenBounds.width / (double) Config.REFERENCE_WIDTH)
        );
        int y = screenBounds.y + (int) Math.round(
                Config.DEFAULT_Y_AT_1440P * (screenBounds.height / (double) Config.REFERENCE_HEIGHT)
        );
        Logger.info("[Overlay] defaultPosition: gameBoundsFound={} x={} y={}", gameBounds.isPresent(), x, y);
        return new Point(x, y);
    }

    private void attachDragListener() {
        MouseAdapter dragAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent mouseEvent) {
                if (!guidePanel.isMoveMode()) return;
                lastDragPoint.setLocation(mouseEvent.getLocationOnScreen());
            }

            @Override
            public void mouseDragged(MouseEvent mouseEvent) {
                if (!guidePanel.isMoveMode()) return;
                Point current = mouseEvent.getLocationOnScreen();
                int deltaX = current.x - lastDragPoint.x;
                int deltaY = current.y - lastDragPoint.y;
                lastDragPoint.setLocation(current);

                Optional<Rectangle> gameBounds = platform.getGameWindowBounds();
                if (gameBounds.isEmpty()) return;

                Rectangle bounds = gameBounds.get();
                Point location = overlayWindow.getLocation();
                int newX = location.x + deltaX;
                int newY = location.y + deltaY;
                int maxX = bounds.x + bounds.width - overlayWindow.getWidth();
                int maxY = bounds.y + bounds.height - overlayWindow.getHeight();
                overlayWindow.setLocation(
                        Math.max(bounds.x, Math.min(newX, maxX)),
                        Math.max(bounds.y, Math.min(newY, maxY))
                );
            }
        };
        guidePanel.addMouseListener(dragAdapter);
        guidePanel.addMouseMotionListener(dragAdapter);
    }

    private static void assertEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Must be called on EDT");
        }
    }
}