package com.hawolt.guide;

import com.hawolt.data.MappingConfig;
import com.hawolt.guide.model.GuideStep;
import com.hawolt.guide.model.Segment;
import com.hawolt.logger.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GuideParser {

    private static final Pattern TOKEN = Pattern.compile(
            "\\{([a-z_]+):([^}]*)\\}|\\{(br)\\}"
    );

    private static final String LEVEL_SUFFIX = "|level:";
    private static final String BANDIT_TOKEN = "bandit";
    private static final String QUEST_TOKEN = "quest";
    private static final String ACT_TOKEN = "act";

    private final MappingConfig mappingConfig;

    public GuideParser(MappingConfig mappingConfig) {
        this.mappingConfig = mappingConfig;
    }

    public List<GuideStep> load(String resourcePath) throws IOException {
        List<String> rawLines = readLines(resourcePath);
        List<GuideStep> steps = new ArrayList<>();
        int currentAct = 1;

        for (String line : rawLines) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (containsActToken(line)) {
                currentAct = extractActNumber(line, currentAct);
            }

            steps.add(parseLine(line, currentAct));
        }

        Logger.info("[GuideParser] Loaded {} steps from {}", steps.size(), resourcePath);
        return steps;
    }

    private boolean containsActToken(String line) {
        Matcher matcher = TOKEN.matcher(line);
        while (matcher.find()) {
            if (ACT_TOKEN.equals(matcher.group(1))) return true;
        }
        return false;
    }

    private int extractActNumber(String line, int fallback) {
        Matcher matcher = TOKEN.matcher(line);
        while (matcher.find()) {
            if (ACT_TOKEN.equals(matcher.group(1))) {
                try {
                    return Integer.parseInt(matcher.group(2).strip());
                } catch (NumberFormatException exception) {
                    Logger.warn("[GuideParser] Could not parse act number from: {}", matcher.group(2));
                }
            }
        }
        return fallback;
    }

    private GuideStep parseLine(String line, int actNumber) {
        List<Segment> segments = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(line);
        int lastIndex = 0;
        String detectedQuestName = null;
        Set<String> detectedBandits = new HashSet<>();

        while (matcher.find()) {
            if (matcher.start() > lastIndex) {
                segments.add(Segment.text(line.substring(lastIndex, matcher.start()), null));
            }

            String fullMatch = matcher.group(0);
            if ("{br}".equals(fullMatch)) {
                segments.add(Segment.lineBreak());
            } else {
                String tokenType = matcher.group(1);
                String tokenValue = matcher.group(2).strip();
                if (QUEST_TOKEN.equals(tokenType) && detectedQuestName == null) {
                    detectedQuestName = tokenValue;
                }
                if (BANDIT_TOKEN.equals(tokenType)) {
                    detectedBandits.add(tokenValue);
                }
                processToken(segments, tokenType, tokenValue);
            }

            lastIndex = matcher.end();
        }

        if (lastIndex < line.length()) {
            segments.add(Segment.text(line.substring(lastIndex), null));
        }

        if (detectedQuestName != null || !detectedBandits.isEmpty()) {
            return new GuideStep(segments, detectedQuestName, actNumber, detectedBandits);
        }
        return new GuideStep(segments);
    }

    private void processToken(List<Segment> segments, String tokenType, String tokenValue) {
        switch (tokenType) {
            case "zone" -> {
                int zoneLevel = 0;
                String zoneName = tokenValue;
                int suffixIndex = tokenValue.indexOf(LEVEL_SUFFIX);
                if (suffixIndex != -1) {
                    zoneName = tokenValue.substring(0, suffixIndex);
                    String levelString = tokenValue.substring(suffixIndex + LEVEL_SUFFIX.length());
                    try {
                        zoneLevel = Integer.parseInt(levelString.strip());
                    } catch (NumberFormatException exception) {
                        Logger.warn("[GuideParser] Could not parse level suffix from zone token: {}", tokenValue);
                    }
                }
                segments.add(Segment.zone(zoneName, mappingConfig.colorForType("zone"), zoneLevel));
            }
            case "town" -> {
                int zoneLevel = 0;
                String zoneName = tokenValue;
                int suffixIndex = tokenValue.indexOf(LEVEL_SUFFIX);
                if (suffixIndex != -1) {
                    zoneName = tokenValue.substring(0, suffixIndex);
                    String levelString = tokenValue.substring(suffixIndex + LEVEL_SUFFIX.length());
                    try {
                        zoneLevel = Integer.parseInt(levelString.strip());
                    } catch (NumberFormatException exception) {
                        Logger.warn("[GuideParser] Could not parse level suffix from town token: {}", tokenValue);
                    }
                }
                segments.add(Segment.zone(zoneName, mappingConfig.colorForType("zone"), zoneLevel));
                segments.add(Segment.image(loadImage("town.png"), "town.png"));
            }
            case "bandit" -> {
            }
            case "npc" -> segments.add(Segment.text(tokenValue, mappingConfig.colorForType("npc")));
            case "quest" -> {
                segments.add(Segment.image(loadImage("quest.png"), "quest.png"));
                segments.add(Segment.text(" ", null));
                segments.add(Segment.text(tokenValue, mappingConfig.colorForType("quest")));
                String npc = mappingConfig.npcForQuest(tokenValue);
                if (npc != null) {
                    if (npc.contains(",")) {
                        String[] npcs = npc.split(",");
                        for (int i = 0; i < npcs.length; i++) {
                            segments.add(Segment.text(" ", null));
                            if (i != 0) segments.add(Segment.text("or ", null));
                            segments.add(Segment.text(npcs[i], mappingConfig.colorForType("npc")));
                        }
                    } else {
                        segments.add(Segment.text(" ", null));
                        segments.add(Segment.text(npc, mappingConfig.colorForType("npc")));
                    }
                }
            }
            case "enemy" -> segments.add(Segment.text(tokenValue, mappingConfig.colorForType("enemy")));
            case "item" -> segments.add(Segment.text(tokenValue, mappingConfig.colorForType("item")));
            case "gem" -> segments.add(Segment.text(tokenValue, mappingConfig.colorForGem(tokenValue)));
            case "waypoint" -> {
                segments.add(Segment.image(loadImage("waypoint.png"), "waypoint.png"));
                segments.add(Segment.text(" ", null));
                segments.add(Segment.text(tokenValue, mappingConfig.colorForType("waypoint")));
            }
            case "portal" -> {
                segments.add(Segment.image(loadImage("portal.png"), "portal.png"));
                segments.add(Segment.text(" ", null));
                segments.add(Segment.text(tokenValue, mappingConfig.colorForType("waypoint")));
            }
            case "trial" -> {
                segments.add(Segment.image(loadImage("trial.png"), "trial.png"));
                segments.add(Segment.text(" ", null));
                segments.add(Segment.text(tokenValue, mappingConfig.colorForType("waypoint")));
            }
            case "crafting" -> {
                segments.add(Segment.image(loadImage("crafting.png"), "crafting.png"));
                segments.add(Segment.text(" Crafting: ", mappingConfig.colorForType("npc")));
                segments.add(Segment.text(tokenValue, mappingConfig.colorForType("npc")));
            }
            case "logout" -> segments.add(Segment.text("Logout", mappingConfig.colorForType("logout")));
            case "hotkey" -> segments.add(Segment.text(tokenValue, mappingConfig.colorForType("hotkey")));
            case "highlight" -> segments.add(Segment.text(tokenValue, mappingConfig.colorForType("highlight")));
            case "rare" -> segments.add(Segment.text(tokenValue, mappingConfig.colorForType("rare_rarity")));
            case "magic" -> segments.add(Segment.text(tokenValue, mappingConfig.colorForType("magic_rarity")));
            case "normal" -> segments.add(Segment.text(tokenValue, mappingConfig.colorForType("normal_rarity")));
            case "class" -> segments.add(Segment.text(tokenValue, mappingConfig.colorForType("class")));
            case "act" -> {
            }
            default -> {
                Logger.warn("[GuideParser] Unknown token type: {}", tokenType);
                segments.add(Segment.text(tokenValue, null));
            }
        }
    }

    private static List<String> readLines(String resourcePath) throws IOException {
        Path workingDirectory = Paths.get(resourcePath);
        if (Files.exists(workingDirectory)) return Files.readAllLines(workingDirectory);

        Path sourceResources = Paths.get("src", "main", "resources", resourcePath);
        if (Files.exists(sourceResources)) return Files.readAllLines(sourceResources);

        String locationPath = GuideParser.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath();
        if (locationPath.matches("^/[A-Za-z]:/.*")) locationPath = locationPath.substring(1);
        Path jarSibling = Paths.get(locationPath).getParent().resolve(resourcePath);
        if (Files.exists(jarSibling)) return Files.readAllLines(jarSibling);

        InputStream stream = GuideParser.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) lines.add(line);
                return lines;
            }
        }

        throw new FileNotFoundException("Could not find '" + resourcePath + "'");
    }

    static Image loadImage(String filename) {
        try {
            URL url = GuideParser.class.getClassLoader().getResource("images/" + filename);
            if (url != null) return ImageIO.read(url);
        } catch (IOException ignored) {
        }
        try {
            Path path = Paths.get("images", filename);
            if (Files.exists(path)) return ImageIO.read(path.toFile());
        } catch (IOException ignored) {
        }
        Logger.warn("[GuideParser] Image not found: {}", filename);
        return null;
    }
}