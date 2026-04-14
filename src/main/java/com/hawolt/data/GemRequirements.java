package com.hawolt.data;

import com.hawolt.logger.Logger;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GemRequirements {

    private static final GemRequirements EMPTY = new GemRequirements(new HashMap<>(), new HashMap<>());

    private final Map<String, String> supportTargets;
    private final Map<String, Integer> requiredGems;

    private GemRequirements(Map<String, Integer> requiredGems, Map<String, String> supportTargets) {
        this.requiredGems = Collections.unmodifiableMap(requiredGems);
        this.supportTargets = Collections.unmodifiableMap(supportTargets);
    }

    public static GemRequirements fromUrl(String url) {
        JSONObject response = BuildCodeParser.fetchFromUrl(url);
        return fromLoadout(response);
    }

    public static GemRequirements fromBuildCode(String importCode) {
        JSONObject response = BuildCodeParser.fetchFromBuild(importCode);
        return fromLoadout(response);
    }

    public static GemRequirements fromSkillSet(JSONObject skillSetObject) {
        return fromLoadout(skillSetObject);
    }

    public static GemRequirements empty() {
        return EMPTY;
    }

    private static GemRequirements fromLoadout(JSONObject skillSetObject) {
        if (skillSetObject == null || skillSetObject.isEmpty()) return EMPTY;
        Map<String, Integer> gems = new HashMap<>();
        Map<String, String> supportTargets = new HashMap<>();
        for (String linkKey : skillSetObject.keySet()) {
            Object linkValue = skillSetObject.opt(linkKey);
            if (!(linkValue instanceof JSONObject linkObject)) continue;
            String activeSkill = resolveActiveSkillInLink(linkObject);
            for (String gemName : linkObject.keySet()) {
                try {
                    gems.merge(gemName, linkObject.getInt(gemName), Integer::sum);
                    if (gemName.endsWith("Support") && activeSkill != null) {
                        supportTargets.put(gemName, activeSkill);
                    }
                } catch (Exception e) {
                    Logger.warn("[GemRequirements] Could not parse quantity for gem: {}", gemName);
                }
            }
        }
        return new GemRequirements(gems, supportTargets);
    }

    private static String resolveActiveSkillInLink(JSONObject linkObject) {
        for (String gemName : linkObject.keySet()) {
            if (!gemName.endsWith("Support")) return gemName;
        }
        return null;
    }

    public boolean isEmpty() {
        return requiredGems.isEmpty();
    }

    public boolean requires(String gemName) {
        return requiredGems.containsKey(gemName);
    }

    public String getSupportTarget(String supportGemName) {
        return supportTargets.get(supportGemName);
    }

    public Map<String, Integer> getRequiredGems() {
        return requiredGems;
    }
}