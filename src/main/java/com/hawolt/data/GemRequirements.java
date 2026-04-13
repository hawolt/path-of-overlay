package com.hawolt.data;

import com.hawolt.logger.Logger;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GemRequirements {

    private static final GemRequirements EMPTY = new GemRequirements(new HashMap<>());

    private final Map<String, Integer> requiredGems;

    private GemRequirements(Map<String, Integer> requiredGems) {
        this.requiredGems = Collections.unmodifiableMap(requiredGems);
    }

    public static GemRequirements fromUrl(String url) {
        JSONObject response = BuildCodeParser.fetchFromUrl(url);
        return fromJsonObject(response);
    }

    public static GemRequirements fromBuildCode(String importCode) {
        JSONObject response = BuildCodeParser.fetchFromBuild(importCode);
        return fromJsonObject(response);
    }

    public static GemRequirements fromSkillSet(JSONObject skillSetObject) {
        return fromJsonObject(skillSetObject);
    }

    public static GemRequirements empty() {
        return EMPTY;
    }

    private static GemRequirements fromJsonObject(JSONObject jsonObject) {
        if (jsonObject == null || jsonObject.isEmpty()) return EMPTY;
        Map<String, Integer> gems = new HashMap<>();
        for (String key : jsonObject.keySet()) {
            try {
                Object value = jsonObject.get(key);
                if (value instanceof Integer intValue) {
                    gems.put(key, intValue);
                } else if (value instanceof JSONObject) {
                    Logger.warn(
                            "[GemRequirements] Key '{}' is a skill set - use fromSkillSet() instead",
                            key
                    );
                } else {
                    gems.put(key, jsonObject.getInt(key));
                }
            } catch (Exception e) {
                Logger.warn("[GemRequirements] Could not parse quantity for gem: {}", key);
            }
        }
        return new GemRequirements(gems);
    }

    public boolean isEmpty() {
        return requiredGems.isEmpty();
    }

    public boolean requires(String gemName) {
        return requiredGems.containsKey(gemName);
    }

    public Map<String, Integer> getRequiredGems() {
        return requiredGems;
    }
}