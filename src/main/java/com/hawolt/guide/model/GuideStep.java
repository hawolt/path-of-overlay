package com.hawolt.guide.model;

import java.util.List;

public class GuideStep {

    private final List<Segment> segments;
    private final String questName;
    private final int actNumber;

    public GuideStep(List<Segment> segments) {
        this.segments = List.copyOf(segments);
        this.questName = null;
        this.actNumber = 0;
    }

    public GuideStep(List<Segment> segments, String questName, int actNumber) {
        this.segments = List.copyOf(segments);
        this.questName = questName;
        this.actNumber = actNumber;
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public String getQuestName() {
        return questName;
    }

    public int getActNumber() {
        return actNumber;
    }

    public boolean isQuestStep() {
        return questName != null;
    }

    public int getZoneLevel() {
        for (Segment segment : segments) {
            if (segment.type == Segment.Type.TEXT && segment.level != 0) return segment.level;
        }
        return 0;
    }

    public String toPlainText() {
        StringBuilder builder = new StringBuilder();
        for (Segment segment : segments) {
            if (segment.type == Segment.Type.TEXT) builder.append(segment.text);
            else if (segment.type == Segment.Type.IMAGE) builder.append("[").append(segment.altText).append("]");
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return toPlainText();
    }
}