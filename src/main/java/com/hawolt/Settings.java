package com.hawolt;

import com.hawolt.hotkey.HotkeyLabel;
import com.hawolt.logger.Logger;
import com.hawolt.platform.windows.WindowsInstallationLocator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

public class Settings {

    private static final Path SETTINGS_FILE = resolveSettingsPath();

    private static final String KEY_HOTKEY_SETTINGS = "hotkey.settings";
    private static final String KEY_HOTKEY_PAUSE = "hotkey.pause";
    private static final String KEY_HOTKEY_TIMER = "hotkey.timer";
    private static final String KEY_HOTKEY_MOVE = "hotkey.move";
    private static final String KEY_HOTKEY_NEXT = "hotkey.next";
    private static final String KEY_HOTKEY_PREV = "hotkey.prev";
    private static final String KEY_POB_INPUT = "pob.input";
    private static final String KEY_LOG_PATH = "log.path";
    private static final String KEY_LOADOUT = "loadout";
    private static final String KEY_BANDIT = "bandit";

    private final Properties properties = new Properties();

    public Settings() {
        loadDefaults();
        if (Files.exists(SETTINGS_FILE)) {
            try (InputStream stream = Files.newInputStream(SETTINGS_FILE)) {
                properties.load(stream);
            } catch (IOException exception) {
                Logger.error("[Settings] Could not load settings: {}", exception.getMessage());
            }
        }
    }

    public boolean isLogPathValid() {
        String path = getLogPath();
        if (path == null || path.isBlank()) return false;
        return Files.exists(Paths.get(path));
    }

    public String getLogPath() {
        return properties.getProperty(KEY_LOG_PATH);
    }

    public void setLogPath(String path) {
        properties.setProperty(KEY_LOG_PATH, path);
    }

    public String getPobInput() {
        return properties.getProperty(KEY_POB_INPUT, "");
    }

    public void setPobInput(String input) {
        properties.setProperty(KEY_POB_INPUT, input);
    }

    public String getBandit() {
        return properties.getProperty(KEY_BANDIT, "Kill all");
    }

    public void setBandit(String bandit) {
        properties.setProperty(KEY_BANDIT, bandit);
    }

    public String getLoadout() {
        return properties.getProperty(KEY_LOADOUT, "Default");
    }

    public void setLoadout(String loadout) {
        properties.setProperty(KEY_LOADOUT, loadout);
    }

    public int getHotkeyNext() {
        return getHotkey(KEY_HOTKEY_NEXT, Config.VK_NEXT);
    }

    public void setHotkeyNext(int encoded) {
        properties.setProperty(KEY_HOTKEY_NEXT, String.valueOf(encoded));
    }

    public int getHotkeyPrev() {
        return getHotkey(KEY_HOTKEY_PREV, Config.VK_PREV);
    }

    public void setHotkeyPrev(int encoded) {
        properties.setProperty(KEY_HOTKEY_PREV, String.valueOf(encoded));
    }

    public int getHotkeyMove() {
        return getHotkey(KEY_HOTKEY_MOVE, Config.VK_MOVE);
    }

    public void setHotkeyMove(int encoded) {
        properties.setProperty(KEY_HOTKEY_MOVE, String.valueOf(encoded));
    }

    public int getHotkeyTimer() {
        return getHotkey(KEY_HOTKEY_TIMER, Config.VK_TIMER);
    }

    public void setHotkeyTimer(int encoded) {
        properties.setProperty(KEY_HOTKEY_TIMER, String.valueOf(encoded));
    }

    public int getHotkeyPause() {
        return getHotkey(KEY_HOTKEY_PAUSE, Config.VK_PAUSE);
    }

    public void setHotkeyPause(int encoded) {
        properties.setProperty(KEY_HOTKEY_PAUSE, String.valueOf(encoded));
    }

    public int getHotkeySettings() {
        return getHotkey(KEY_HOTKEY_SETTINGS, Config.VK_SETTINGS);
    }

    public void setHotkeySettings(int encoded) {
        properties.setProperty(KEY_HOTKEY_SETTINGS, String.valueOf(encoded));
    }

    public void save() {
        try (OutputStream stream = Files.newOutputStream(SETTINGS_FILE)) {
            properties.store(stream, "poe-overlay settings");
        } catch (IOException exception) {
            Logger.error("[Settings] Could not save settings: {}", exception.getMessage());
        }
    }

    private void loadDefaults() {
        properties.setProperty(KEY_HOTKEY_NEXT, String.valueOf(Config.VK_NEXT));
        properties.setProperty(KEY_HOTKEY_PREV, String.valueOf(Config.VK_PREV));
        properties.setProperty(KEY_HOTKEY_MOVE, String.valueOf(Config.VK_MOVE));
        properties.setProperty(KEY_HOTKEY_TIMER, String.valueOf(Config.VK_TIMER));
        properties.setProperty(KEY_HOTKEY_PAUSE, String.valueOf(Config.VK_PAUSE));
        properties.setProperty(KEY_HOTKEY_SETTINGS, String.valueOf(Config.VK_SETTINGS));

        Optional<String> located = WindowsInstallationLocator.findClientTxt();
        located.ifPresentOrElse(
                path -> {
                    Logger.info("[Settings] Auto-detected Client.txt: {}", path);
                    properties.setProperty(KEY_LOG_PATH, path);
                },
                () -> Logger.warn("[Settings] Could not auto-detect Client.txt - user must configure manually.")
        );
    }

    private int getHotkey(String key, int fallback) {
        String raw = properties.getProperty(key);
        if (raw == null) return fallback;
        try {
            int encoded = Integer.parseInt(raw);
            int[] win = HotkeyLabel.toWindowsHotkey(encoded);
            if (win == null) {
                Logger.warn("[Settings] Ignoring non-registerable hotkey for '{}' - using default.", key);
                return fallback;
            }
            return encoded;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static Path resolveSettingsPath() {
        try {
            return Paths.get(
                    Settings.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            ).getParent().resolve("settings.properties");
        } catch (Exception exception) {
            return Paths.get("settings.properties");
        }
    }
}