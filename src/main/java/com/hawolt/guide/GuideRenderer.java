package com.hawolt.guide;

import com.hawolt.guide.model.GuideStep;
import com.hawolt.guide.model.Segment;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class GuideRenderer {

    private static final int IMAGE_GAP = 4;
    private static final int LINE_GAP = 6;

    private record Item(String text, Color color, Image image, int width, int imageHeight) {
    }

    public record Layout(List<List<Item>> lines, int contentWidth, int contentHeight) {
    }

    public static Layout measure(GuideStep step, Font font) {
        FontMetrics fontMetrics = getFontMetrics(font);
        List<List<Item>> lines = buildLines(step, fontMetrics);

        int widest = 0;
        for (List<Item> line : lines) {
            int lineWidth = 0;
            for (Item item : line) lineWidth += item.width();
            if (lineWidth > widest) widest = lineWidth;
        }

        int lineHeight = lineHeight(fontMetrics);
        int totalHeight = lines.size() * lineHeight + (lines.size() - 1) * LINE_GAP;

        return new Layout(lines, widest, totalHeight);
    }

    public static void render(Graphics2D graphics, Layout layout, int x, int y, Font font) {
        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB
        );
        graphics.setFont(font);
        FontMetrics fontMetrics = graphics.getFontMetrics(font);
        drawLines(graphics, layout.lines(), fontMetrics, x, y);
    }

    private static List<List<Item>> buildLines(GuideStep step, FontMetrics fontMetrics) {
        List<List<Item>> lines = new ArrayList<>();
        List<Item> currentLine = new ArrayList<>();
        boolean previousWasImage = false;
        int imageHeight = imageHeight(fontMetrics);

        for (Segment segment : step.getSegments()) {

            if (segment.type == Segment.Type.LINEBREAK) {
                lines.add(currentLine);
                currentLine = new ArrayList<>();
                previousWasImage = false;
                continue;
            }

            if (segment.type == Segment.Type.IMAGE && segment.image != null) {
                int sourceWidth = segment.image.getWidth(null);
                int sourceHeight = segment.image.getHeight(null);
                if (sourceWidth <= 0 || sourceHeight <= 0) continue;
                int scaledWidth = (int) Math.round(sourceWidth * (imageHeight / (double) sourceHeight)
                ) + IMAGE_GAP;
                currentLine.add(new Item(
                        null,
                        null,
                        segment.image,
                        scaledWidth,
                        imageHeight
                ));
                previousWasImage = true;
                continue;
            }

            if (segment.type == Segment.Type.TEXT && segment.text != null) {
                Color color = segment.color != null ? segment.color : Color.WHITE;
                String text = previousWasImage ? segment.text.stripLeading() : segment.text;
                if (text.isEmpty()) {
                    previousWasImage = false;
                    continue;
                }
                currentLine.add(new Item(
                        text,
                        color,
                        null,
                        fontMetrics.stringWidth(text),
                        0
                ));
                previousWasImage = false;
            }
        }

        if (!currentLine.isEmpty()) lines.add(currentLine);
        if (lines.isEmpty()) lines.add(new ArrayList<>());
        return lines;
    }

    private static void drawLines(Graphics2D graphics, List<List<Item>> lines, FontMetrics fontMetrics, int x, int y) {
        int lineHeight = lineHeight(fontMetrics);
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            List<Item> line = lines.get(lineIndex);
            int lineTop = y + lineIndex * (lineHeight + LINE_GAP);
            int baseline = lineTop
                    + fontMetrics.getAscent()
                    + (lineHeight - fontMetrics.getAscent() - fontMetrics.getDescent()) / 2;
            int cursorX = x;
            for (Item item : line) {
                if (item.image() != null) {
                    int imageWidth = item.width() - IMAGE_GAP;
                    int imageY = lineTop + (lineHeight - item.imageHeight()) / 2;
                    graphics.drawImage(
                            item.image(),
                            cursorX,
                            imageY,
                            imageWidth,
                            item.imageHeight(),
                            null
                    );
                    cursorX += item.width();
                } else {
                    graphics.setColor(item.color());
                    graphics.drawString(item.text(), cursorX, baseline);
                    cursorX += item.width();
                }
            }
        }
    }

    private static int imageHeight(FontMetrics fontMetrics) {
        return fontMetrics.getAscent();
    }

    private static int lineHeight(FontMetrics fontMetrics) {
        return fontMetrics.getAscent() + fontMetrics.getDescent();
    }

    private static FontMetrics getFontMetrics(Font font) {
        BufferedImage buffer = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = buffer.createGraphics();
        graphics.setFont(font);
        FontMetrics fontMetrics = graphics.getFontMetrics();
        graphics.dispose();
        return fontMetrics;
    }
}