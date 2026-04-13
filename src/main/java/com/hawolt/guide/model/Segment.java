package com.hawolt.guide.model;

import java.awt.*;

public class Segment {

    public enum Type {TEXT, IMAGE, LINEBREAK}

    public final Type type;
    public final String text;
    public final Color color;
    public final Image image;
    public final String altText;
    public final int level;

    private Segment(Type type, String text, Color color, Image image, String altText, int level) {
        this.type = type;
        this.text = text;
        this.color = color;
        this.image = image;
        this.altText = altText;
        this.level = level;
    }

    public static Segment text(String text, Color color) {
        return new Segment(Type.TEXT, text, color, null, null, 0);
    }

    public static Segment zone(String text, Color color, int level) {
        return new Segment(Type.TEXT, text, color, null, null, level);
    }

    public static Segment image(Image image, String altText) {
        return new Segment(Type.IMAGE, null, null, image, altText, 0);
    }

    public static Segment lineBreak() {
        return new Segment(Type.LINEBREAK, null, null, null, null, 0);
    }
}