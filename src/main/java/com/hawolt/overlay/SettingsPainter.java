package com.hawolt.overlay;

import com.hawolt.Config;
import com.hawolt.hotkey.HotkeyLabel;

import java.awt.*;
import java.util.List;

class SettingsPainter {

    static final Color COL_TOGGLE_OFF_BORDER = new Color(150, 60, 60, 255);
    static final Color COL_TOGGLE_OFF_TEXT = new Color(210, 100, 100, 255);
    static final Color COL_TOGGLE_ON_BORDER = new Color(60, 150, 60, 255);
    static final Color COL_TOGGLE_ON_TEXT = new Color(100, 210, 100, 255);
    static final Color COL_RECAL_OK_BORDER = new Color(60, 160, 60, 255);
    static final Color COL_LOAD_OK_BORDER = new Color(60, 160, 60, 255);
    static final Color COL_ACTIVE_BORDER = new Color(180, 150, 60, 255);
    static final Color COL_RECAL_OK_TEXT = new Color(100, 220, 100, 255);
    static final Color COL_LOAD_OK_TEXT = new Color(100, 220, 100, 255);
    static final Color COL_BORDER_DISABLED = new Color(50, 45, 30, 120);
    static final Color COL_DISABLED = new Color(100, 95, 80, 180);
    static final Color COL_BORDER = new Color(80, 65, 30, 200);
    static final Color COL_LABEL = new Color(180, 168, 140, 210);
    static final Color COL_TITLE = new Color(220, 200, 140, 255);
    static final Color COL_VALUE = new Color(230, 215, 170, 255);
    static final Color COL_CLOSE = new Color(160, 60, 40, 230);
    static final Color COL_HINT = new Color(110, 100, 75, 180);
    static final Color COL_GOLD = new Color(200, 168, 75, 255);
    static final Color BG_FIELD_DISABLED = new Color(0, 0, 0, 60);
    static final Color BG_TOGGLE_OFF = new Color(55, 20, 20, 220);
    static final Color BG_TOGGLE_ON = new Color(20, 55, 20, 220);
    static final Color BG_RECAL_OK = new Color(20, 60, 20, 220);
    static final Color BG_LOAD_OK = new Color(20, 60, 20, 220);
    static final Color BG_LISTEN = new Color(120, 100, 40, 130);
    static final Color BG_OUTER = new Color(10, 8, 4, 255);
    static final Color BG_FIELD = new Color(0, 0, 0, 130);
    static final Color BG_SAVE = new Color(35, 28, 8, 220);
    static final Color COL_DANGER_BORDER = new Color(150, 50, 50, 255);
    static final Color COL_DANGER_TEXT = new Color(210, 90, 90, 255);
    static final Color BG_DANGER = new Color(50, 15, 15, 220);

    static final int ROW_H = 46;
    static final int PAD = 24;
    static final int GAP = 10;
    static final int ARC = 10;
    static final int W = 500;

    private SettingsPainter() {
    }

    static void paintFieldBackground(
            Graphics2D graphics,
            Font labelFont,
            int x,
            int y,
            int width,
            String label,
            boolean active
    ) {
        graphics.setColor(active ? BG_LISTEN : BG_FIELD);
        graphics.fillRoundRect(x, y, width, ROW_H, ARC, ARC);
        graphics.setColor(active ? COL_ACTIVE_BORDER : COL_BORDER);
        graphics.setStroke(new BasicStroke(1f));
        graphics.drawRoundRect(x, y, width, ROW_H, ARC, ARC);
        graphics.setFont(labelFont);
        graphics.setColor(COL_LABEL);
        FontMetrics labelMetrics = graphics.getFontMetrics();
        int baseline = y + (ROW_H + labelMetrics.getAscent() - labelMetrics.getDescent()) / 2;
        graphics.drawString(label, x + 12, baseline);
    }

    static void paintField(
            Graphics2D graphics,
            Font labelFont,
            Font valueFont,
            int x,
            int y,
            int width,
            String label,
            String value,
            boolean active,
            boolean rightAlign
    ) {
        graphics.setColor(active ? BG_LISTEN : BG_FIELD);
        graphics.fillRoundRect(x, y, width, ROW_H, ARC, ARC);
        graphics.setColor(active ? COL_ACTIVE_BORDER : COL_BORDER);
        graphics.setStroke(new BasicStroke(1f));
        graphics.drawRoundRect(x, y, width, ROW_H, ARC, ARC);

        graphics.setFont(labelFont);
        graphics.setColor(COL_LABEL);
        FontMetrics labelMetrics = graphics.getFontMetrics();
        int baseline = y + (ROW_H + labelMetrics.getAscent() - labelMetrics.getDescent()) / 2;
        graphics.drawString(label, x + 12, baseline);

        graphics.setFont(valueFont);
        graphics.setColor(active ? COL_GOLD : COL_VALUE);
        FontMetrics valueMetrics = graphics.getFontMetrics();
        int valueBaseline = y + (ROW_H + valueMetrics.getAscent() - valueMetrics.getDescent()) / 2;

        if (rightAlign) {
            int valueX = x + width - valueMetrics.stringWidth(value) - 12;
            graphics.drawString(value, valueX, valueBaseline);
        } else {
            int labelWidth = labelMetrics.stringWidth(label) + 20;
            int availableWidth = width - labelWidth - 12;
            String truncated = truncateToFit(value, valueMetrics, availableWidth);
            graphics.drawString(truncated, x + labelWidth, valueBaseline);
        }
    }

    static void paintFieldCycleable(
            Graphics2D graphics,
            Font labelFont,
            Font valueFont,
            Font hintFont,
            int x,
            int y,
            int width,
            String label,
            String value,
            boolean enabled,
            int index,
            int total
    ) {
        graphics.setColor(enabled ? BG_FIELD : BG_FIELD_DISABLED);
        graphics.fillRoundRect(x, y, width, ROW_H, ARC, ARC);
        graphics.setColor(enabled ? COL_BORDER : COL_BORDER_DISABLED);
        graphics.setStroke(new BasicStroke(1f));
        graphics.drawRoundRect(x, y, width, ROW_H, ARC, ARC);

        graphics.setFont(labelFont);
        graphics.setColor(enabled ? COL_LABEL : COL_DISABLED);
        FontMetrics labelMetrics = graphics.getFontMetrics();
        int baseline = y + (ROW_H + labelMetrics.getAscent() - labelMetrics.getDescent()) / 2;
        graphics.drawString(label, x + 12, baseline);

        graphics.setFont(hintFont);
        graphics.setColor(enabled ? COL_HINT : COL_DISABLED);
        FontMetrics hintMetrics = graphics.getFontMetrics();
        String indicator = (index + 1) + "/" + total;
        int indicatorX = x + width - hintMetrics.stringWidth(indicator) - 12;
        int indicatorBaseline = y + (ROW_H + hintMetrics.getAscent() - hintMetrics.getDescent()) / 2;
        graphics.drawString(indicator, indicatorX, indicatorBaseline);

        graphics.setFont(valueFont);
        graphics.setColor(enabled ? COL_VALUE : COL_DISABLED);
        FontMetrics valueMetrics = graphics.getFontMetrics();
        int valueBaseline = y + (ROW_H + valueMetrics.getAscent() - valueMetrics.getDescent()) / 2;
        int indicatorWidth = hintMetrics.stringWidth(indicator) + 8;
        int valueMaxX = x + width - indicatorWidth - 12;
        int labelWidth = labelMetrics.stringWidth(label) + 20;
        int availableWidth = valueMaxX - (x + labelWidth);
        String truncated = truncateToFit(value, valueMetrics, availableWidth);
        graphics.drawString(truncated, x + labelWidth, valueBaseline);
    }

    static void paintButton(
            Graphics2D graphics,
            Font font,
            int x,
            int y,
            int width,
            String label,
            Color background,
            Color border,
            Color textColor
    ) {
        graphics.setColor(background);
        graphics.fillRoundRect(x, y, width, ROW_H, ARC, ARC);
        graphics.setColor(border);
        graphics.drawRoundRect(x, y, width, ROW_H, ARC, ARC);
        graphics.setFont(font);
        graphics.setColor(textColor);
        FontMetrics fontMetrics = graphics.getFontMetrics();
        graphics.drawString(
                label,
                x + (width - fontMetrics.stringWidth(label)) / 2,
                y + (ROW_H + fontMetrics.getAscent() - fontMetrics.getDescent()) / 2
        );
    }

    static String truncateToFit(String text, FontMetrics metrics, int maxWidth) {
        if (metrics.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        for (int i = text.length() - 1; i >= 0; i--) {
            String candidate = ellipsis + text.substring(text.length() - i);
            if (metrics.stringWidth(candidate) <= maxWidth) return candidate;
        }
        return ellipsis;
    }
}