package com.hawolt.data;

import com.hawolt.logger.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MappingConfig {

    private static final String RESOURCE_NAME = "mapping.json";
    private static final String GEMS_RESOURCE_NAME = "gems.minimal.json";

    private final Map<String, Color> typeColors = new HashMap<>();
    private final Map<String, String> questNpcs = new HashMap<>();
    private final Map<String, String> gemVendorsByAct = new HashMap<>();
    private final Map<String, String> gemVendorQuestOverrides = new HashMap<>();
    private final Map<String, Color> gemColors = new HashMap<>();
    private final List<String> knownNpcs = new ArrayList<>();
    private final List<String> knownQuests = new ArrayList<>();
    private final List<String> knownEnemies = new ArrayList<>();
    private final List<String> knownTowns = new ArrayList<>();

    private MappingConfig() {
    }

    public static MappingConfig load() {
        MappingConfig config = new MappingConfig();
        try {
            String json = readResource(RESOURCE_NAME);
            config.parse(new JSONObject(json));
        } catch (IOException e) {
            Logger.error("[MappingConfig] Failed to load mapping.json: {}", e.getMessage());
        }
        try {
            String json = readResource(GEMS_RESOURCE_NAME);
            config.parseGems(new JSONObject(json));
        } catch (IOException e) {
            Logger.error("[MappingConfig] Failed to load gems.minimal.json: {}", e.getMessage());
        }
        return config;
    }

    private static String readResource(String resourceName) throws IOException {
        Path workingDir = Paths.get(resourceName);
        if (Files.exists(workingDir)) return Files.readString(workingDir);

        Path sourceResources = Paths.get("src", "main", "resources", resourceName);
        if (Files.exists(sourceResources)) return Files.readString(sourceResources);

        InputStream stream = MappingConfig.class.getClassLoader().getResourceAsStream(resourceName);
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
        throw new IOException("Could not find " + resourceName);
    }

    private void parse(JSONObject root) {
        if (root.has("types")) {
            JSONObject types = root.getJSONObject("types");
            for (String key : types.keySet()) {
                try {
                    typeColors.put(key, Color.decode(types.getString(key)));
                } catch (NumberFormatException e) {
                    Logger.warn("[MappingConfig] Could not parse color for type: {}", key);
                }
            }
        }

        if (root.has("quest_npcs")) {
            JSONObject npcs = root.getJSONObject("quest_npcs");
            for (String quest : npcs.keySet()) {
                Object value = npcs.get(quest);
                if (value instanceof String) {
                    questNpcs.put(quest, (String) value);
                } else if (value instanceof JSONArray) {
                    questNpcs.put(
                            quest,
                            ((JSONArray) value).toList()
                                    .stream()
                                    .map(Object::toString)
                                    .map(name -> name.replaceAll("\"", ""))
                                    .collect(Collectors.joining(","))
                    );
                }
            }
        }

        if (root.has("gem_vendors")) {
            JSONObject vendors = root.getJSONObject("gem_vendors");
            for (String key : vendors.keySet()) {
                if (key.equals("quest_override")) {
                    JSONObject overrides = vendors.getJSONObject("quest_override");
                    for (String quest : overrides.keySet()) {
                        gemVendorQuestOverrides.put(quest, overrides.getString(quest));
                    }
                } else {
                    gemVendorsByAct.put(key, vendors.getString(key));
                }
            }
        }

        if (root.has("entities")) {
            JSONObject entities = root.getJSONObject("entities");
            knownNpcs.addAll(jsonArrayToList(entities.optJSONArray("npcs")));
            knownQuests.addAll(jsonArrayToList(entities.optJSONArray("quests")));
            knownEnemies.addAll(jsonArrayToList(entities.optJSONArray("enemies")));
            knownTowns.addAll(jsonArrayToList(entities.optJSONArray("towns")));
        }
    }

    private void parseGems(JSONObject root) {
        for (String gemId : root.keySet()) {
            JSONObject gemEntry = root.optJSONObject(gemId);
            if (gemEntry == null) continue;
            String name = gemEntry.optString("name", "");
            String color = gemEntry.optString("color", "b");
            if (name.isBlank()) continue;
            String colorType = switch (color) {
                case "r" -> "r_skill";
                case "g" -> "g_skill";
                default -> "b_skill";
            };
            Color resolved = typeColors.get(colorType);
            if (resolved != null) {
                gemColors.put(name, resolved);
            }
        }
        Logger.info("[MappingConfig] Loaded colors for {} gems", gemColors.size());
    }

    private List<String> jsonArrayToList(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) result.add(array.getString(i));
        return result;
    }

    public Color colorForType(String type) {
        return typeColors.getOrDefault(type, Color.WHITE);
    }

    public Color colorForGem(String gemName) {
        return gemColors.getOrDefault(gemName, typeColors.getOrDefault("b_skill", Color.WHITE));
    }

    public String npcForQuest(String questName) {
        return questNpcs.get(questName);
    }

    public String resolveGemVendor(String questName, int actNumber) {
        String override = gemVendorQuestOverrides.get(questName);
        if (override != null) return override;
        return gemVendorsByAct.getOrDefault(String.valueOf(actNumber), null);
    }

    public boolean isTown(String zoneName) {
        return knownTowns.contains(zoneName);
    }

    public boolean isKnownNpc(String name) {
        return knownNpcs.contains(name);
    }

    public boolean isKnownEnemy(String name) {
        return knownEnemies.contains(name);
    }

    public boolean isKnownQuest(String name) {
        return knownQuests.contains(name);
    }
}