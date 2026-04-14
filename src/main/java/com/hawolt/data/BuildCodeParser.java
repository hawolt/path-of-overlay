package com.hawolt.data;

import com.hawolt.logger.Logger;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

public class BuildCodeParser {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;

    private record SiteInfo(String id, String regexURL, String downloadURL) {
    }

    private static final SiteInfo[] KNOWN_SITES = {
            new SiteInfo(
                    "pobbin",
                    "(?:https?://)?(?:www\\.)?pobb\\.in/([\\w-]+).*",
                    "https://pobb.in/%s/raw"
            ),
            new SiteInfo(
                    "poeninja",
                    "(?:https?://)?(?:www\\.)?poe\\.ninja/builds?/.*[?&]b=([\\w]+).*",
                    "https://poe.ninja/api/data/CharacterItems?character=%s&account="
            ),
            new SiteInfo(
                    "pastebin",
                    "(?:https?://)?(?:www\\.)?pastebin\\.com/(?:raw/)?([\\w]+).*",
                    "https://pastebin.com/raw/%s"
            ),
            new SiteInfo(
                    "poeskilltree",
                    "(?:https?://)?(?:www\\.)?poeskilltree\\.com/([\\w-]+).*",
                    "https://poeskilltree.com/%s/raw"
            ),
    };

    private BuildCodeParser() {
    }

    public static JSONObject fetchFromUrl(String link) {
        try {
            String downloadUrl = resolveDownloadUrl(link);
            if (downloadUrl == null) {
                Logger.warn("[BuildCodeParser] No matching site found for URL: {}", link);
                return new JSONObject();
            }
            Logger.info("[BuildCodeParser] Resolved download URL: {}", downloadUrl);
            String body = get(downloadUrl);
            if (body == null || body.isBlank()) {
                Logger.warn("[BuildCodeParser] Empty response from: {}", downloadUrl);
                return new JSONObject();
            }
            if (body.trim().startsWith("<")) {
                return parse(body);
            }
            return fetchFromBuild(body.trim());
        } catch (Exception e) {
            Logger.error("[BuildCodeParser] Failed to fetch from URL: {}", e.getMessage());
            return new JSONObject();
        }
    }

    public static JSONObject fetchFromBuild(String importCode) {
        try {
            String xml = decodeBuildCode(importCode);
            return parse(xml);
        } catch (Exception e) {
            Logger.error("[BuildCodeParser] Failed to decode build code: {}", e.getMessage());
            return new JSONObject();
        }
    }

    public static String decodeBuildCode(String importCode) throws IOException {
        int pad = importCode.length() % 4;
        if (pad != 0) importCode += "=".repeat(4 - pad);
        byte[] compressed = Base64.getUrlDecoder().decode(importCode);
        try (
                InflaterInputStream inflaterInputStream = new InflaterInputStream(
                        new ByteArrayInputStream(compressed)
                );
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()
        ) {
            inflaterInputStream.transferTo(byteArrayOutputStream);
            return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
        }
    }

    private static String resolveDownloadUrl(String link) {
        for (SiteInfo site : KNOWN_SITES) {
            Pattern pattern = Pattern.compile(site.regexURL);
            Matcher matcher = pattern.matcher(link);
            if (matcher.matches()) {
                String id = matcher.group(1);
                return String.format(site.downloadURL, id);
            }
        }
        return null;
    }

    private static String get(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "exile-overlay");
        try (InputStream stream = connection.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static JSONObject parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
            );
            document.getDocumentElement().normalize();

            JSONObject result = new JSONObject();

            JSONObject loadouts = new JSONObject();

            NodeList skillSets = document.getElementsByTagName("SkillSet");
            for (int i = 0; i < skillSets.getLength(); i++) {
                Element skillSetElement = (Element) skillSets.item(i);
                String title = skillSetElement.getAttribute("title");
                if (title == null || title.isBlank()) {
                    title = "Default";
                }

                Map<String, Integer> gems = new LinkedHashMap<>();

                NodeList gemNodes = skillSetElement.getElementsByTagName("Gem");
                for (int j = 0; j < gemNodes.getLength(); j++) {
                    Element gemElement = (Element) gemNodes.item(j);
                    String nameSpec = gemElement.getAttribute("nameSpec");
                    String countStr = gemElement.getAttribute("count");
                    if (nameSpec == null || nameSpec.isBlank()) continue;
                    int count = 1;
                    if (countStr != null && !countStr.isBlank() && !"nil".equals(countStr)) {
                        try {
                            count = Integer.parseInt(countStr);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    gems.merge(nameSpec, count, Integer::sum);
                }

                if (!gems.isEmpty()) {
                    JSONObject skillSetObject = new JSONObject();
                    for (Map.Entry<String, Integer> entry : gems.entrySet()) {
                        skillSetObject.put(entry.getKey(), entry.getValue());
                    }
                    loadouts.put(title, skillSetObject);
                }
            }

            NodeList configSets = document.getElementsByTagName("ConfigSet");
            String bandit = "Kill all";
            for (int i = 0; i < configSets.getLength(); i++) {
                Element configSet = (Element) configSets.item(i);
                NodeList inputs = configSet.getElementsByTagName("Input");
                for (int j = 0; j < inputs.getLength(); j++) {
                    Element input = (Element) inputs.item(j);
                    if ("bandit".equals(input.getAttribute("name"))) {
                        String val = input.getAttribute("string");
                        if (val != null && !val.isBlank()) {
                            bandit = "Help " + val;
                        }
                        break;
                    }
                }
            }

            result.put("loadouts", loadouts);
            result.put("bandit", bandit);

            Logger.info("[BuildCodeParser] Extracted {} skill sets from POB XML", result.length());
            return result;

        } catch (Exception e) {
            Logger.error("[BuildCodeParser] Failed to parse POB XML: {}", e.getMessage());
            return new JSONObject();
        }
    }
}