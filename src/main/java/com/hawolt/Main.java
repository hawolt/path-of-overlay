package com.hawolt;

import com.hawolt.data.BuildCodeParser;
import com.hawolt.guide.ActTimer;
import com.hawolt.hotkey.HotkeyLabel;
import com.hawolt.logger.Logger;
import com.hawolt.overlay.OverlayFrame;
import com.hawolt.overlay.SettingsOverlay;
import com.hawolt.platform.Platform;
import com.hawolt.platform.PlatformFactory;
import com.hawolt.watch.LogWatcher;
import com.hawolt.watch.ProcessWatchdog;
import org.json.JSONObject;

import javax.swing.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        System.setProperty("java.awt.headless", "false");

        Logger.info("[Main] Application starting");

        Platform platform = PlatformFactory.create();
        Logger.info("[Main] Platform created: {}", platform.getClass().getSimpleName());

        Settings settings = new Settings();
        Logger.info(
                "[Main] Settings loaded - logPath={} hotkeyNext={} hotkeyPrev={} hotkeyMove={} hotkeyTimer={} hotkeyPause={} hotkeySettings={}",
                settings.getLogPath(),
                settings.getHotkeyNext(),
                settings.getHotkeyPrev(),
                settings.getHotkeyMove(),
                settings.getHotkeyTimer(),
                settings.getHotkeyPause(),
                settings.getHotkeySettings()
        );

        ActTimer actTimer = new ActTimer();
        CountDownLatch shutdown = new CountDownLatch(1);

        OverlayFrame[] overlayRef = new OverlayFrame[1];
        SwingUtilities.invokeLater(() -> overlayRef[0] = new OverlayFrame(actTimer, platform));
        while (overlayRef[0] == null) Thread.sleep(10);
        OverlayFrame overlay = overlayRef[0];
        Logger.info("[Main] OverlayFrame created");

        String savedPob = settings.getPobInput();
        if (savedPob != null && !savedPob.isBlank()) {
            Logger.info("[Main] Applying saved PoB on startup");
            JSONObject parsed;
            if (savedPob.startsWith("http://") || savedPob.startsWith("https://")) {
                parsed = BuildCodeParser.fetchFromUrl(savedPob);
            } else {
                parsed = BuildCodeParser.fetchFromBuild(savedPob);
            }
            if (!parsed.isEmpty()) {
                String bandit = parsed.optString("bandit", "Kill all");
                JSONObject loadouts = parsed.optJSONObject("loadouts");
                String savedLoadout = settings.getLoadout();
                settings.setBandit(bandit);
                SwingUtilities.invokeLater(() -> {
                    overlay.setBandit(bandit);
                    if (loadouts != null && !loadouts.isEmpty()) {
                        overlay.setLoadouts(loadouts, savedLoadout);
                    }
                });
                Logger.info("[Main] Saved PoB applied successfully - bandit={} loadout={}", bandit, savedLoadout);
            } else {
                Logger.warn("[Main] Saved PoB parse returned empty result");
            }
        }

        AtomicReference<SettingsOverlay> settingsRef = new AtomicReference<>();
        AtomicReference<Thread> logThreadRef = new AtomicReference<>();

        Runnable startLogWatcher = () -> {
            Thread previous = logThreadRef.get();
            if (previous != null && previous.isAlive()) {
                Logger.info("[Main] Interrupting previous log-watcher thread");
                previous.interrupt();
                try {
                    previous.join(1000);
                } catch (InterruptedException ignored) {
                }
            }
            Thread logThread = new Thread(new LogWatcher(
                    settings,
                    (zone, level) -> overlay.seekToZone(zone, level),
                    (zone, level) -> SwingUtilities.invokeLater(() -> overlay.seekToZoneFromStart(zone, level)),
                    focused -> SwingUtilities.invokeLater(() -> overlay.onGameFocusChanged(focused)),
                    ingame -> SwingUtilities.invokeLater(() -> {
                        overlay.onIngameChanged(ingame);
                        SettingsOverlay settingsOverlay = settingsRef.get();
                        if (settingsOverlay != null) settingsOverlay.onIngameChanged(ingame);
                    }),
                    () -> SwingUtilities.invokeLater(overlay::onCampaignComplete),
                    () -> {
                        actTimer.reset();
                        SwingUtilities.invokeLater(overlay::onCampaignReset);
                    },
                    detectedClass -> SwingUtilities.invokeLater(() -> overlay.setActiveClass(detectedClass))
            ), "log-watcher");
            logThread.setDaemon(true);
            logThread.start();
            logThreadRef.set(logThread);
            Logger.info("[Main] LogWatcher started: {}", settings.getLogPath());
        };

        ProcessWatchdog watchdog = new ProcessWatchdog(platform, running -> {
            Logger.info("[Main] Process watchdog state changed: running={}", running);
            SwingUtilities.invokeLater(() -> {
                overlay.onGameRunningChanged(running);
                SettingsOverlay settingsOverlay = settingsRef.get();
                if (settingsOverlay != null) settingsOverlay.onGameRunningChanged(running);
            });
        });
        Thread watchdogThread = new Thread(watchdog, "process-watchdog");
        watchdogThread.setDaemon(true);
        watchdogThread.start();
        Logger.info("[Main] ProcessWatchdog thread started");

        AtomicBoolean keepRunning = new AtomicBoolean(true);
        AtomicBoolean hotkeysEnabled = new AtomicBoolean(true);
        AtomicReference<Thread> pumpRef = new AtomicReference<>();

        Runnable startPump = new Runnable() {
            @Override
            public void run() {
                Logger.info("[Main] Starting hotkey-pump thread");
                Thread pumpThread = new Thread(() -> {
                    Logger.info("[HotkeyPump] Thread started, keepRunning={}", keepRunning.get());
                    while (keepRunning.get()) {
                        if (!hotkeysEnabled.get()) {
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException interruptedException) {
                                Thread.currentThread().interrupt();
                            }
                            continue;
                        }

                        Logger.info(
                                "[HotkeyPump] Registering hotkeys - next={} prev={} move={} timer={} pause={} settings={}",
                                settings.getHotkeyNext(),
                                settings.getHotkeyPrev(),
                                settings.getHotkeyMove(),
                                settings.getHotkeyTimer(),
                                settings.getHotkeyPause(),
                                settings.getHotkeySettings()
                        );

                        registerOne(platform, 1, settings.getHotkeyNext(), "Next");
                        registerOne(platform, 2, settings.getHotkeyPrev(), "Prev");
                        registerOne(platform, 3, settings.getHotkeyMove(), "Move");
                        registerOne(platform, 4, settings.getHotkeyTimer(), "Timer");
                        registerOne(platform, 5, settings.getHotkeySettings(), "Settings");
                        registerOne(platform, 6, settings.getHotkeyPause(), "Pause");
                        boolean reloadOk = platform.registerHotKey(7, Config.VK_RELOAD_GUIDE, Config.MOD_NOREPEAT);
                        Logger.info(
                                "[HotkeyPump] id=7 label=ReloadGuide vk=0x{} mod=0x{} ok={}",
                                Integer.toHexString(Config.VK_RELOAD_GUIDE),
                                Integer.toHexString(Config.MOD_NOREPEAT),
                                reloadOk
                        );

                        Logger.info("[HotkeyPump] Entering GetMessage loop");
                        platform.runHotKeyLoop(id -> {
                            Logger.info("[HotkeyPump] Hotkey fired: id={}", id);
                            switch (id) {
                                case 1 -> {
                                    Logger.info("[HotkeyPump] Dispatching: next");
                                    SwingUtilities.invokeLater(overlay::next);
                                }
                                case 2 -> {
                                    Logger.info("[HotkeyPump] Dispatching: prev");
                                    SwingUtilities.invokeLater(overlay::prev);
                                }
                                case 3 -> {
                                    Logger.info("[HotkeyPump] Dispatching: toggleMoveMode");
                                    SwingUtilities.invokeLater(overlay::toggleMoveMode);
                                }
                                case 4 -> {
                                    Logger.info("[HotkeyPump] Dispatching: toggleTimerOverlay");
                                    SwingUtilities.invokeLater(overlay::toggleTimerOverlay);
                                }
                                case 5 -> {
                                    Logger.info("[HotkeyPump] Dispatching: openSettings");
                                    SettingsOverlay settingsOverlay = settingsRef.get();
                                    if (settingsOverlay != null) SwingUtilities.invokeLater(settingsOverlay::toggle);
                                    else
                                        Logger.warn("[HotkeyPump] settingsRef is null - settings overlay not ready yet");
                                }
                                case 6 -> {
                                    Logger.info("[HotkeyPump] Dispatching: toggleTimerPause");
                                    SwingUtilities.invokeLater(overlay::toggleTimerPause);
                                }
                                case 7 -> {
                                    Logger.info("[HotkeyPump] Dispatching: reloadGuide");
                                    SwingUtilities.invokeLater(overlay::reloadGuide);
                                }
                                default -> Logger.warn("[HotkeyPump] Unknown hotkey id={}", id);
                            }
                        });

                        Logger.info("[HotkeyPump] GetMessage loop exited, unregistering hotkeys");
                        for (int id = 1; id <= 7; id++) {
                            platform.unregisterHotKey(id);
                            Logger.info("[HotkeyPump] Unregistered id={}", id);
                        }

                        if (keepRunning.get()) {
                            Logger.info(
                                    "[HotkeyPump] Loop will restart (keepRunning=true, hotkeysEnabled={})",
                                    hotkeysEnabled.get()
                            );
                        }
                    }
                    Logger.info("[HotkeyPump] Thread exiting (keepRunning=false)");
                }, "hotkey-pump");
                pumpThread.setDaemon(true);
                pumpThread.start();
                pumpRef.set(pumpThread);
                Logger.info("[Main] hotkey-pump thread launched");
            }
        };
        startPump.run();

        Runnable suspendHotkeys = () -> {
            Logger.info("[Main] Suspending hotkeys");
            hotkeysEnabled.set(false);
            platform.stopHotKeyLoop();
        };

        Runnable resumeHotkeys = () -> {
            Logger.info("[Main] Resuming hotkeys");
            hotkeysEnabled.set(true);
            platform.stopHotKeyLoop();
        };

        CountDownLatch settingsReady = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            Logger.info("[Main] Creating SettingsOverlay");
            SettingsOverlay settingsOverlay = new SettingsOverlay(
                    platform,
                    settings,
                    () -> {
                        Logger.info("[Main] Settings saved - applying loadout and restarting log watcher");
                        SettingsOverlay current = settingsRef.get();
                        if (current != null) {
                            String selectedBandit = settings.getBandit();
                            SwingUtilities.invokeLater(() -> overlay.setBandit(selectedBandit));
                            JSONObject loadouts = current.getLoadouts();
                            String selectedLoadout = current.getSelectedLoadout();
                            if (loadouts != null && !loadouts.isEmpty()) {
                                settings.setLoadout(selectedLoadout);
                                SwingUtilities.invokeLater(() -> overlay.setLoadouts(loadouts, selectedLoadout));
                            }
                        }
                        platform.stopHotKeyLoop();
                        startLogWatcher.run();
                    },
                    () -> {
                        SettingsOverlay current = settingsRef.get();
                        if (current != null) current.hide();
                    },
                    () -> SwingUtilities.invokeLater(overlay::forceShow),
                    () -> SwingUtilities.invokeLater(overlay::forceHide),
                    () -> {
                        Logger.info("[Main] Recalibrate triggered - resetting act timer and restarting log watcher");
                        actTimer.reset();
                        SwingUtilities.invokeLater(overlay::onCampaignReset);
                        startLogWatcher.run();
                    },
                    open -> {
                        Logger.info("[Main] Settings open state changed: open={}", open);
                        SwingUtilities.invokeLater(() -> overlay.onSettingsOpenChanged(open));
                    },
                    suspendHotkeys,
                    resumeHotkeys,
                    pobInput -> {
                        if (pobInput == null || pobInput.isBlank()) return;
                        Logger.info("[Main] PoB input received, parsing build");
                        JSONObject parsed;
                        if (pobInput.startsWith("http://") || pobInput.startsWith("https://")) {
                            parsed = BuildCodeParser.fetchFromUrl(pobInput);
                        } else {
                            parsed = BuildCodeParser.fetchFromBuild(pobInput);
                        }
                        if (parsed.isEmpty()) {
                            Logger.warn("[Main] PoB parse returned empty result");
                            return;
                        }
                        String bandit = parsed.optString("bandit", "Kill all");
                        JSONObject loadouts = parsed.optJSONObject("loadouts");
                        String savedLoadout = settings.getLoadout();
                        SwingUtilities.invokeLater(() -> {
                            overlay.setBandit(bandit);
                            SettingsOverlay current = settingsRef.get();
                            if (current != null) {
                                current.setBandit(bandit);
                                if (loadouts != null && !loadouts.isEmpty()) {
                                    current.setLoadouts(loadouts, savedLoadout);
                                }
                            }
                        });
                        Logger.info(
                                "[Main] PoB parsed - bandit={} loadouts={}",
                                bandit,
                                loadouts != null ? loadouts.keySet() : "none"
                        );
                    }
            );
            settingsOverlay.onGameRunningChanged(watchdog.isRunning());
            Logger.info("[Main] SettingsOverlay created, initial gameRunning={}", watchdog.isRunning());
            settingsRef.set(settingsOverlay);
            settingsReady.countDown();
        });
        settingsReady.await();
        Logger.info("[Main] SettingsOverlay ready");

        if (!settings.isLogPathValid()) {
            Logger.warn("[Main] No valid log path - opening settings for first-time configuration.");
            SettingsOverlay settingsOverlay = settingsRef.get();
            if (settingsOverlay != null) SwingUtilities.invokeLater(settingsOverlay::toggle);
        } else {
            Logger.info("[Main] Log path valid, starting log watcher");
            startLogWatcher.run();
        }

        if (platform.supportsTray()) {
            platform.installTray(
                    () -> {
                        Logger.info("[Main] Tray: open settings clicked");
                        SettingsOverlay settingsOverlay = settingsRef.get();
                        if (settingsOverlay != null) SwingUtilities.invokeLater(settingsOverlay::toggle);
                        else Logger.warn("[Main] Tray: settingsRef is null");
                    },
                    () -> {
                        Logger.info("[Main] Tray: exit clicked");
                        shutdown.countDown();
                        System.exit(0);
                    }
            );
            Logger.info("[Main] Tray icon installed");
        } else {
            Logger.info("[Main] No tray - use settings hotkey in-game");
        }

        shutdown.await();
    }

    private static void registerOne(Platform platform, int id, int encoded, String label) {
        int[] win = HotkeyLabel.toWindowsHotkey(encoded);
        if (win == null) {
            Logger.warn("[HotkeyPump] id={} label={} encoded={} -> skipped (no binding)", id, label, encoded);
            return;
        }
        boolean ok = platform.registerHotKey(id, win[0], win[1]);
        Logger.info(
                "[HotkeyPump] id={} label={} vk=0x{} mod=0x{} ok={}",
                id,
                label,
                Integer.toHexString(win[0]),
                Integer.toHexString(win[1]),
                ok
        );
    }
}