package com.hawolt.data;

import com.hawolt.logger.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RewardsConfig {

    private static final String RESOURCE_NAME = "rewards.min.json";
    private static final String ALL_CLASSES = "all";

    private JSONObject questRewards = new JSONObject();
    private String activeClass = null;

    private RewardsConfig() {
    }

    public static RewardsConfig load() {
        RewardsConfig config = new RewardsConfig();
        try {
            String json = readResource();
            JSONObject root = new JSONObject(json);
            if (root.has("quest_rewards")) {
                config.questRewards = root.getJSONObject("quest_rewards");
            }
        } catch (IOException e) {
            Logger.error("[RewardsConfig] Failed to load rewards.min.json: {}", e.getMessage());
        }
        return config;
    }

    private static String readResource() throws IOException {
        Path workingDir = Paths.get(RESOURCE_NAME);
        if (Files.exists(workingDir)) return Files.readString(workingDir);

        Path sourceResources = Paths.get("src", "main", "resources", RESOURCE_NAME);
        if (Files.exists(sourceResources)) return Files.readString(sourceResources);

        InputStream stream = RewardsConfig.class.getClassLoader().getResourceAsStream(RESOURCE_NAME);
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
        throw new IOException("Could not find rewards.min.json");
    }

    public void setActiveClass(String className) {
        this.activeClass = className;
        Logger.info("[RewardsConfig] Active class set to: {}", className);
    }

    public String getActiveClass() {
        return activeClass;
    }

    public String findFirstRewardGem(String questName, GemRequirements requirements, Set<String> excluded) {
        if (activeClass == null || requirements.isEmpty()) return null;
        JSONObject questData = questRewards.optJSONObject(questName);
        if (questData == null) return null;
        JSONArray rewards = questData.optJSONArray("rewards");
        if (rewards == null) return null;
        for (int i = 0; i < rewards.length(); i++) {
            JSONObject entry = rewards.optJSONObject(i);
            if (entry == null) continue;
            if (!isAvailableForClass(entry)) continue;
            String name = entry.optString("name", "");
            if (requirements.requires(name) && !excluded.contains(name)) return name;
        }
        return null;
    }

    public List<String> findVendorGems(String questName, GemRequirements requirements, Set<String> excluded) {
        List<String> result = new ArrayList<>();
        if (activeClass == null || requirements.isEmpty()) return result;
        JSONObject questData = questRewards.optJSONObject(questName);
        if (questData == null) return result;
        JSONArray vendor = questData.optJSONArray("vendor");
        if (vendor == null) return result;
        for (int i = 0; i < vendor.length(); i++) {
            JSONObject entry = vendor.optJSONObject(i);
            if (entry == null) continue;
            if (!isAvailableForClass(entry)) continue;
            String name = entry.optString("name", "");
            if (requirements.requires(name) && !excluded.contains(name) && !result.contains(name)) {
                result.add(name);
            }
        }
        return result;
    }

    private boolean isAvailableForClass(JSONObject entry) {
        JSONArray classes = entry.optJSONArray("classes");
        if (classes == null) return false;
        for (int i = 0; i < classes.length(); i++) {
            String cls = classes.optString(i, "");
            if (ALL_CLASSES.equals(cls) || cls.equalsIgnoreCase(activeClass)) return true;
        }
        return false;
    }
}