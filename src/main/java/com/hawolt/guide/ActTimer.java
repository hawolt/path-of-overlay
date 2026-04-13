package com.hawolt.guide;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class ActTimer {

    public enum Act {
        ACT_1("I", "The Twilight Strand"),
        ACT_2("II", "The Southern Forest"),
        ACT_3("III", "The City of Sarn"),
        ACT_4("IV", "The Aqueduct"),
        ACT_5("V", "The Slave Pens"),
        ACT_6("VI", "Lioneye's Watch"),
        ACT_7("VII", "The Bridge Encampment"),
        ACT_8("VIII", "The Sarn Ramparts"),
        ACT_9("IX", "The Blood Aqueduct"),
        ACT_10("X", "Oriath Docks");

        public final String roman;
        public final String startZone;

        Act(String roman, String startZone) {
            this.roman = roman;
            this.startZone = startZone;
        }
    }

    private static final String EPILOGUE_ZONE = "Karui Shores";

    private final Map<Act, Duration> completedActs = new LinkedHashMap<>();
    private Act currentAct = null;
    private Instant actStart = null;
    private Duration pausedElapsed = Duration.ZERO;
    private Instant pausedAt = null;
    private boolean campaignComplete = false;

    public synchronized void onZoneEntered(String zoneName, int areaLevel) {
        if (EPILOGUE_ZONE.equalsIgnoreCase(zoneName)) {
            if (currentAct != null && actStart != null) {
                completedActs.put(currentAct, resolveElapsed());
            }
            currentAct = null;
            actStart = null;
            pausedElapsed = Duration.ZERO;
            pausedAt = null;
            campaignComplete = true;
            return;
        }

        for (Act act : Act.values()) {
            if (!act.startZone.equalsIgnoreCase(zoneName)) continue;
            if (act == Act.ACT_1 && areaLevel == 1) reset();
            if (act == currentAct) return;
            if (currentAct != null && act.ordinal() < currentAct.ordinal()) return;
            if (currentAct != null && act.ordinal() > currentAct.ordinal() + 1) return;
            if (currentAct != null && actStart != null) {
                completedActs.put(currentAct, resolveElapsed());
            }
            currentAct = act;
            actStart = Instant.now();
            pausedElapsed = Duration.ZERO;
            pausedAt = null;
            return;
        }
    }

    public synchronized void reset() {
        completedActs.clear();
        currentAct = null;
        actStart = null;
        pausedElapsed = Duration.ZERO;
        pausedAt = null;
        campaignComplete = false;
    }

    public synchronized void togglePause() {
        if (currentAct == null || actStart == null) return;
        if (pausedAt != null) {
            actStart = Instant.now().minus(pausedElapsed);
            pausedAt = null;
        } else {
            pausedElapsed = resolveElapsed();
            pausedAt = Instant.now();
        }
    }

    public synchronized boolean isPaused() {
        return pausedAt != null;
    }

    public synchronized Act getCurrentAct() {
        return currentAct;
    }

    public synchronized Duration getCurrentElapsed() {
        if (currentAct == null || actStart == null) return null;
        return resolveElapsed();
    }

    public synchronized Map<Act, Duration> getCompletedActs() {
        return new LinkedHashMap<>(completedActs);
    }

    public synchronized boolean isCampaignComplete() {
        return campaignComplete;
    }

    private Duration resolveElapsed() {
        if (pausedAt != null) return pausedElapsed;
        return Duration.between(actStart, Instant.now());
    }

    public static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        return String.format(
                "%02d:%02d:%02d",
                seconds / 3600,
                (seconds % 3600) / 60,
                seconds % 60
        );
    }
}