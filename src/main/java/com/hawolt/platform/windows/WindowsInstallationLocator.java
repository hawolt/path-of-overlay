package com.hawolt.platform.windows;

import com.hawolt.logger.Logger;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public final class WindowsInstallationLocator {

    private static final String LOG_RELATIVE = "logs\\Client.txt";

    private static final String GGG_KEY = "SOFTWARE\\GrindingGearGames\\Path of Exile";
    private static final String GGG_VALUE = "InstallLocation";

    private static final String STEAM_UNINSTALL_KEY =
            "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Steam App 238960";
    private static final String STEAM_INSTALL_VALUE = "InstallLocation";

    private static final String EPIC_MANIFESTS =
            "C:\\ProgramData\\Epic\\EpicGamesLauncher\\Data\\Manifests";
    private static final String EPIC_DISPLAY_NAME = "\"DisplayName\":\"Path of Exile\"";
    private static final String EPIC_INSTALL_KEY = "\"InstallLocation\":\"";

    private WindowsInstallationLocator() {
    }

    public static Optional<String> findClientTxt() {
        return findInstallDir().map(dir -> dir + "\\" + LOG_RELATIVE);
    }

    private static Optional<String> findInstallDir() {
        Optional<String> result;

        result = fromGggRegistry();
        if (result.isPresent()) {
            Logger.info("[WindowsInstallationLocator] Found via GGG registry: {}", result.get());
            return result;
        }

        result = fromSteamRegistry();
        if (result.isPresent()) {
            Logger.info("[WindowsInstallationLocator] Found via Steam registry: {}", result.get());
            return result;
        }

        result = fromEpicManifests();
        if (result.isPresent()) {
            Logger.info("[WindowsInstallationLocator] Found via Epic manifests: {}", result.get());
            return result;
        }

        Logger.warn("[WindowsInstallationLocator] Could not locate PoE installation.");
        return Optional.empty();
    }

    private static Optional<String> fromGggRegistry() {
        try {
            if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, GGG_KEY)) {
                return Optional.empty();
            }
            String dir = Advapi32Util.registryGetStringValue(
                    WinReg.HKEY_CURRENT_USER,
                    GGG_KEY,
                    GGG_VALUE
            );
            return validateInstallDir(dir);
        } catch (Exception e) {
            Logger.debug("[WindowsInstallationLocator] GGG registry lookup failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<String> fromSteamRegistry() {
        try {
            if (!Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, STEAM_UNINSTALL_KEY)) {
                return Optional.empty();
            }
            String dir = Advapi32Util.registryGetStringValue(
                    WinReg.HKEY_LOCAL_MACHINE,
                    STEAM_UNINSTALL_KEY,
                    STEAM_INSTALL_VALUE
            );
            return validateInstallDir(dir);
        } catch (Exception e) {
            Logger.debug("[WindowsInstallationLocator] Steam registry lookup failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<String> fromEpicManifests() {
        File manifestDir = new File(EPIC_MANIFESTS);
        if (!manifestDir.exists() || !manifestDir.isDirectory()) return Optional.empty();

        File[] items = manifestDir.listFiles(
                (dir, name) -> name.endsWith(".item")
        );
        if (items == null) return Optional.empty();

        for (File item : items) {
            try {
                String content = Files.readString(item.toPath());
                if (!content.contains(EPIC_DISPLAY_NAME)) continue;

                int keyIdx = content.indexOf(EPIC_INSTALL_KEY);
                if (keyIdx == -1) continue;

                int start = keyIdx + EPIC_INSTALL_KEY.length();
                int end = content.indexOf("\"", start);
                if (end == -1) continue;

                String dir = content.substring(start, end).replace("\\\\", "\\");
                return validateInstallDir(dir);
            } catch (IOException e) {
                Logger.debug("[WindowsInstallationLocator] Failed to read Epic manifest {}: {}", item.getName(), e.getMessage());
            }
        }
        return Optional.empty();
    }

    private static Optional<String> validateInstallDir(String dir) {
        if (dir == null || dir.isBlank()) return Optional.empty();
        String normalised = dir.stripTrailing();
        while (normalised.endsWith("\\") || normalised.endsWith("/")) {
            normalised = normalised.substring(0, normalised.length() - 1);
        }
        Path clientTxt = Paths.get(normalised, LOG_RELATIVE);
        if (Files.exists(clientTxt)) return Optional.of(normalised);
        Logger.debug("[WindowsInstallationLocator] Directory found but Client.txt missing: {}", normalised);
        return Optional.empty();
    }
}