package com.hawolt.guide;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class ActTimer {

    public enum Act {
        ACT_1("I", "The Twilight Strand", 1, 13, 13),
        ACT_2("II", "The Southern Forest", 13, 23, 13),
        ACT_3("III", "The City of Sarn", 23, 33, 23),
        ACT_4("IV", "The Aqueduct", 33, 40, 33),
        ACT_5("V", "The Slave Pens", 41, 45, 41),
        ACT_6("VI", "Lioneye's Watch", 45, 50, 50),
        ACT_7("VII", "The Bridge Encampment", 50, 55, 55),
        ACT_8("VIII", "The Sarn Ramparts", 55, 60, 55),
        ACT_9("IX", "The Blood Aqueduct", 61, 64, 67),
        ACT_10("X", "Oriath Docks", 64, 67, 69);

        public final String roman;
        public final String startZone;
        public final int actStartLevel;
        public final int actEndLevel;
        public final int areaLevel;

        Act(String roman, String startZone, int actStartLevel, int actEndLevel, int areaLevel) {
            this.roman = roman;
            this.startZone = startZone;
            this.actStartLevel = actStartLevel;
            this.actEndLevel = actEndLevel;
            this.areaLevel = areaLevel;
        }
    }

    private static final String EPILOGUE_ZONE = "Karui Shores";

    private final Map<Act, Duration> completedActs = new LinkedHashMap<>();
    private Duration pausedElapsed = Duration.ZERO;
    private Act currentAct = null;
    private Instant actStart = null;
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

        if (currentAct != null) {
            handleKnownAct(zoneName);
        } else {
            handleUnknownAct(zoneName, areaLevel);
        }
    }

    private void handleKnownAct(String zoneName) {
        for (Act act : Act.values()) {
            if (!act.startZone.equalsIgnoreCase(zoneName)) continue;
            if (act == currentAct) return;
            if (act.ordinal() < currentAct.ordinal()) return;
            if (act.ordinal() > currentAct.ordinal() + 1) return;
            completedActs.put(currentAct, resolveElapsed());
            currentAct = act;
            actStart = Instant.now();
            pausedElapsed = Duration.ZERO;
            pausedAt = null;
            return;
        }
    }

    private void handleUnknownAct(String zoneName, int areaLevel) {
        for (Act act : Act.values()) {
            if (act.startZone.equalsIgnoreCase(zoneName) && act.areaLevel == areaLevel) {
                transitionToAct(act);
                return;
            }
        }

        for (Act act : Act.values()) {
            if (areaLevel >= act.actStartLevel && areaLevel <= act.actEndLevel) {
                transitionToAct(act);
                return;
            }
        }
    }

    private void transitionToAct(Act act) {
        currentAct = act;
        actStart = Instant.now();
        pausedElapsed = Duration.ZERO;
        pausedAt = null;
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