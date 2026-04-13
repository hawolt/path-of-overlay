package com.hawolt.watch;

import com.hawolt.Settings;
import com.hawolt.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogWatcher implements Runnable {

    private static final String ZONE_TRIGGER = ": You have entered ";
    private static final String FOCUS_GAINED = "[WINDOW] Gained focus";
    private static final String FOCUS_LOST = "[WINDOW] Lost focus";
    private static final String SCENE_SOURCE = "[SCENE] Set Source [";
    private static final String SCENE_NULL = "[SCENE] Set Source [(null)]";
    private static final String SCENE_UNKNOWN = "[SCENE] Set Source [(unknown)]";
    private static final String AREA_GEN_TRIGGER = "] Generating level ";
    private static final String LEVEL_UP_TRIGGER = ") is now level ";
    private static final String EPILOGUE_ZONE = "Karui Shores";
    private static final String NEW_CHARACTER_ZONE = "The Twilight Strand";
    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "\\] : [^(]+\\(([A-Za-z]+)\\) is now level "
    );
    private static final long POLL_INTERVAL_MS = 250;
    private static final int REVERSE_CHUNK_SIZE = 4096;

    private final BiConsumer<String, Integer> onZoneEntered;
    private final BiConsumer<String, Integer> onInitialZoneEntered;
    private final Consumer<Boolean> onIngameChanged;
    private final Consumer<Boolean> onFocusChanged;
    private final Consumer<String> onClassDetected;
    private final Runnable onCampaignComplete;
    private final Runnable onCampaignReset;
    private final Settings settings;

    public LogWatcher(
            Settings settings,
            BiConsumer<String, Integer> onZoneEntered,
            BiConsumer<String, Integer> onInitialZoneEntered,
            Consumer<Boolean> onFocusChanged,
            Consumer<Boolean> onIngameChanged,
            Runnable onCampaignComplete,
            Runnable onCampaignReset,
            Consumer<String> onClassDetected
    ) {
        this.settings = settings;
        this.onZoneEntered = onZoneEntered;
        this.onInitialZoneEntered = onInitialZoneEntered;
        this.onFocusChanged = onFocusChanged;
        this.onIngameChanged = onIngameChanged;
        this.onCampaignComplete = onCampaignComplete;
        this.onCampaignReset = onCampaignReset;
        this.onClassDetected = onClassDetected;
    }

    @Override
    public void run() {
        File logFile = new File(settings.getLogPath());
        Logger.info("[LogWatcher] Watching: {}", logFile.getAbsolutePath());
        Logger.info("[LogWatcher] File exists: {}", logFile.exists());

        long lastPosition = logFile.exists() ? logFile.length() : 0;
        Logger.info("[LogWatcher] Starting at byte position: {}", lastPosition);

        boolean campaignComplete = false;
        int pendingAreaLevel = 0;

        if (logFile.exists()) {
            boolean focus = scanLastFocusState(logFile);
            boolean ingame = scanLastIngameState(logFile);
            String lastZone = scanLastZone(logFile);
            int lastAreaLevel = scanLastAreaLevel(logFile);
            String lastClass = scanLastClass(logFile);
            Logger.info(
                    "[LogWatcher] Initial scan - focus={} ingame={} lastZone={} lastAreaLevel={} lastClass={}",
                    focus, ingame, lastZone, lastAreaLevel, lastClass
            );
            if (lastZone != null && EPILOGUE_ZONE.equalsIgnoreCase(lastZone)) {
                campaignComplete = true;
                Logger.info("[LogWatcher] Initial zone is epilogue - campaign already complete, suppressing.");
                onCampaignComplete.run();
            }
            Logger.info("[LogWatcher] Firing onFocusChanged({})", focus);
            onFocusChanged.accept(focus);
            Logger.info("[LogWatcher] Firing onIngameChanged({})", ingame);
            onIngameChanged.accept(ingame);
            if (lastClass != null) {
                Logger.info("[LogWatcher] Firing onClassDetected({})", lastClass);
                onClassDetected.accept(lastClass);
            }
            if (!campaignComplete && lastZone != null) {
                Logger.info("[LogWatcher] Firing initial zone: {} (level {})", lastZone, lastAreaLevel);
                onInitialZoneEntered.accept(lastZone, lastAreaLevel);
            } else if (campaignComplete) {
                Logger.info("[LogWatcher] Skipping initial zone fire - campaign already complete");
            } else {
                Logger.warn("[LogWatcher] No initial zone found in log - overlay will not seek on startup");
            }
        } else {
            Logger.warn("[LogWatcher] Log file not found at '{}' - sending focus=false ingame=false", logFile.getAbsolutePath());
            onFocusChanged.accept(false);
            onIngameChanged.accept(false);
        }

        Logger.info("[LogWatcher] Entering poll loop (interval={}ms)", POLL_INTERVAL_MS);
        int pollCount = 0;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (!logFile.exists()) {
                    if (pollCount % 20 == 0)
                        Logger.warn("[LogWatcher] Log file missing, waiting...");
                    pollCount++;
                    Thread.sleep(POLL_INTERVAL_MS);
                    continue;
                }

                long fileLength = logFile.length();
                if (fileLength < lastPosition) {
                    Logger.warn("[LogWatcher] Log file shrank ({} -> {}), resetting position to 0", lastPosition, fileLength);
                    lastPosition = 0;
                }

                if (fileLength > lastPosition) {
                    long newBytes = fileLength - lastPosition;
                    Logger.debug("[LogWatcher] {} new bytes detected at position {}", newBytes, lastPosition);
                    try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
                        raf.seek(lastPosition);
                        String line;
                        int lineCount = 0;
                        while ((line = raf.readLine()) != null) {
                            lineCount++;
                            if (line.contains(FOCUS_GAINED)) {
                                Logger.info("[LogWatcher] Focus GAINED detected");
                                onFocusChanged.accept(true);
                            } else if (line.contains(FOCUS_LOST)) {
                                Logger.info("[LogWatcher] Focus LOST detected");
                                onFocusChanged.accept(false);
                            } else if (line.contains(SCENE_NULL) || line.contains(SCENE_UNKNOWN)) {
                                Logger.info("[LogWatcher] Scene null/unknown - firing onIngameChanged(false)");
                                onIngameChanged.accept(false);
                            } else if (line.contains(SCENE_SOURCE)) {
                                Logger.info("[LogWatcher] Scene source set - firing onIngameChanged(true)");
                                onIngameChanged.accept(true);
                            } else if (line.contains(LEVEL_UP_TRIGGER)) {
                                Matcher matcher = CLASS_PATTERN.matcher(line);
                                if (matcher.find()) {
                                    String detectedClass = matcher.group(1);
                                    Logger.info("[LogWatcher] Class detected: {}", detectedClass);
                                    onClassDetected.accept(detectedClass);
                                }
                            } else if (line.contains(AREA_GEN_TRIGGER)) {
                                int levelIndex = line.indexOf(AREA_GEN_TRIGGER) + AREA_GEN_TRIGGER.length();
                                int spaceIndex = line.indexOf(' ', levelIndex);
                                if (spaceIndex != -1) {
                                    try {
                                        int areaLevel = Integer.parseInt(line.substring(levelIndex, spaceIndex));
                                        Logger.debug("[LogWatcher] Area level parsed: {}", areaLevel);
                                        pendingAreaLevel = areaLevel;
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                            } else if (line.contains(ZONE_TRIGGER)) {
                                int idx = line.indexOf(ZONE_TRIGGER);
                                String zoneName = line.substring(idx + ZONE_TRIGGER.length()).trim();
                                if (zoneName.endsWith(".")) {
                                    zoneName = zoneName.substring(0, zoneName.length() - 1);
                                }
                                int capturedLevel = pendingAreaLevel;
                                pendingAreaLevel = 0;
                                Logger.info("[LogWatcher] ZONE ENTERED: {} (level {})", zoneName, capturedLevel);
                                if (campaignComplete && NEW_CHARACTER_ZONE.equalsIgnoreCase(zoneName)) {
                                    Logger.info("[LogWatcher] New character detected in Twilight Strand - firing onCampaignReset");
                                    campaignComplete = false;
                                    onCampaignReset.run();
                                } else if (!campaignComplete) {
                                    if (EPILOGUE_ZONE.equalsIgnoreCase(zoneName)) {
                                        Logger.info("[LogWatcher] Epilogue zone reached - firing onCampaignComplete");
                                        campaignComplete = true;
                                        onCampaignComplete.run();
                                    } else {
                                        Logger.info("[LogWatcher] Firing onZoneEntered({}, {})", zoneName, capturedLevel);
                                        onZoneEntered.accept(zoneName, capturedLevel);
                                    }
                                } else {
                                    Logger.info("[LogWatcher] Zone ignored - campaign already complete: {}", zoneName);
                                }
                            }
                        }
                        Logger.debug("[LogWatcher] Read {} lines from new data", lineCount);
                        lastPosition = raf.getFilePointer();
                    } catch (IOException e) {
                        Logger.error("[LogWatcher] File read error: {}", e.getMessage());
                    }
                } else {
                    if (pollCount % 40 == 0)
                        Logger.debug("[LogWatcher] Polling... position={} fileSize={}", lastPosition, fileLength);
                }

                pollCount++;
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Logger.info("[LogWatcher] Interrupted, stopping poll loop");
                Thread.currentThread().interrupt();
            }
        }

        Logger.info("[LogWatcher] Thread exiting");
    }

    private static String scanLastClass(File logFile) {
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long length = raf.length();
            long pos = length;
            StringBuilder partial = new StringBuilder();

            while (pos > 0) {
                long chunkStart = Math.max(0, pos - REVERSE_CHUNK_SIZE);
                int chunkSize = (int) (pos - chunkStart);
                raf.seek(chunkStart);
                byte[] buf = new byte[chunkSize];
                raf.readFully(buf);
                String chunk = new String(buf) + partial;
                String[] lines = chunk.split("\n");

                for (int i = lines.length - 1; i >= 0; i--) {
                    String line = lines[i];
                    if (line.contains(LEVEL_UP_TRIGGER)) {
                        Matcher matcher = CLASS_PATTERN.matcher(line);
                        if (matcher.find()) return matcher.group(1);
                    }
                }

                partial = new StringBuilder(lines[0]);
                pos = chunkStart;
            }
        } catch (IOException e) {
            Logger.error("[LogWatcher] Error scanning last class: {}", e.getMessage());
        }
        return null;
    }

    private static boolean scanLastFocusState(File logFile) {
        return scanLastMatch(logFile, FOCUS_GAINED, FOCUS_LOST, false);
    }

    private static boolean scanLastIngameState(File logFile) {
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long length = raf.length();
            long pos = length;
            StringBuilder partial = new StringBuilder();

            while (pos > 0) {
                long chunkStart = Math.max(0, pos - REVERSE_CHUNK_SIZE);
                int chunkSize = (int) (pos - chunkStart);
                raf.seek(chunkStart);
                byte[] buf = new byte[chunkSize];
                raf.readFully(buf);
                String chunk = new String(buf) + partial;
                String[] lines = chunk.split("\n");

                for (int i = lines.length - 1; i >= 0; i--) {
                    String line = lines[i];
                    if (line.contains(SCENE_NULL) || line.contains(SCENE_UNKNOWN)) return false;
                    if (line.contains(SCENE_SOURCE)) return true;
                }

                partial = new StringBuilder(lines[0]);
                pos = chunkStart;
            }
        } catch (IOException e) {
            Logger.error("[LogWatcher] Error scanning ingame state: {}", e.getMessage());
        }
        return false;
    }

    private static String scanLastZone(File logFile) {
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long length = raf.length();
            long pos = length;
            StringBuilder partial = new StringBuilder();

            while (pos > 0) {
                long chunkStart = Math.max(0, pos - REVERSE_CHUNK_SIZE);
                int chunkSize = (int) (pos - chunkStart);
                raf.seek(chunkStart);
                byte[] buf = new byte[chunkSize];
                raf.readFully(buf);
                String chunk = new String(buf) + partial;
                String[] lines = chunk.split("\n");

                for (int i = lines.length - 1; i >= 0; i--) {
                    String line = lines[i];
                    if (line.contains(ZONE_TRIGGER)) {
                        int idx = line.indexOf(ZONE_TRIGGER);
                        String zone = line.substring(idx + ZONE_TRIGGER.length()).trim();
                        if (zone.endsWith(".")) zone = zone.substring(0, zone.length() - 1);
                        return zone;
                    }
                }

                partial = new StringBuilder(lines[0]);
                pos = chunkStart;
            }
        } catch (IOException e) {
            Logger.error("[LogWatcher] Error scanning last zone: {}", e.getMessage());
        }
        return null;
    }

    private static int scanLastAreaLevel(File logFile) {
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long length = raf.length();
            long pos = length;
            StringBuilder partial = new StringBuilder();

            while (pos > 0) {
                long chunkStart = Math.max(0, pos - REVERSE_CHUNK_SIZE);
                int chunkSize = (int) (pos - chunkStart);
                raf.seek(chunkStart);
                byte[] buf = new byte[chunkSize];
                raf.readFully(buf);
                String chunk = new String(buf) + partial;
                String[] lines = chunk.split("\n");

                for (int i = lines.length - 1; i >= 0; i--) {
                    String line = lines[i];
                    if (line.contains(AREA_GEN_TRIGGER)) {
                        int levelIndex = line.indexOf(AREA_GEN_TRIGGER) + AREA_GEN_TRIGGER.length();
                        int spaceIndex = line.indexOf(' ', levelIndex);
                        if (spaceIndex != -1) {
                            try {
                                return Integer.parseInt(line.substring(levelIndex, spaceIndex));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }

                partial = new StringBuilder(lines[0]);
                pos = chunkStart;
            }
        } catch (IOException e) {
            Logger.error("[LogWatcher] Error scanning last area level: {}", e.getMessage());
        }
        return 0;
    }

    private static boolean scanLastMatch(
            File logFile,
            String trueMarker,
            String falseMarker,
            boolean defaultValue
    ) {
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long length = raf.length();
            long pos = length;
            StringBuilder partial = new StringBuilder();

            while (pos > 0) {
                long chunkStart = Math.max(0, pos - REVERSE_CHUNK_SIZE);
                int chunkSize = (int) (pos - chunkStart);
                raf.seek(chunkStart);
                byte[] buf = new byte[chunkSize];
                raf.readFully(buf);
                String chunk = new String(buf) + partial;
                String[] lines = chunk.split("\n");

                for (int i = lines.length - 1; i >= 0; i--) {
                    String line = lines[i];
                    if (line.contains(trueMarker)) return true;
                    if (line.contains(falseMarker)) return false;
                }

                partial = new StringBuilder(lines[0]);
                pos = chunkStart;
            }
        } catch (IOException e) {
            Logger.error("[LogWatcher] Error scanning log: {}", e.getMessage());
        }
        return defaultValue;
    }
}