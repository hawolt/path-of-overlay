package com.hawolt.guide;

import javax.swing.*;
import java.awt.event.ActionListener;

public class SlideAnimator {

    private static final int FPS = 60;
    private static final int DURATION_MS = 180;
    private static final int TICK_DELAY = 1000 / FPS;

    public enum Direction {FORWARD, BACKWARD}

    private Timer timer;
    private float progress = 1f;
    private long startTime;
    private Direction direction = Direction.FORWARD;
    private Runnable onMidpoint;

    private final JComponent target;

    public SlideAnimator(JComponent target) {
        this.target = target;
    }

    public void play(Direction direction, Runnable preStart, Runnable onMidpoint) {
        this.direction = direction;
        this.onMidpoint = onMidpoint;

        if (timer != null && timer.isRunning()) timer.stop();

        progress = 0f;
        startTime = System.nanoTime();

        if (preStart != null) preStart.run();

        ActionListener onTick = event -> {
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000L;
            progress = Math.min(1f, (float) elapsedMs / DURATION_MS);

            if (progress >= 0.5f && this.onMidpoint != null) {
                this.onMidpoint.run();
                this.onMidpoint = null;
            }

            target.repaint();

            if (progress >= 1f) ((Timer) event.getSource()).stop();
        };

        timer = new Timer(TICK_DELAY, onTick);
        timer.setInitialDelay(0);
        timer.start();
    }

    public void play(Direction direction, Runnable onMidpoint) {
        play(direction, null, onMidpoint);
    }

    public float alpha() {
        if (progress < 0.5f) {
            return 1f - easeIn(progress / 0.5f);
        } else {
            return easeOut((progress - 0.5f) / 0.5f);
        }
    }

    public float slideY() {
        int sign = direction == Direction.FORWARD ? -1 : 1;
        if (progress < 0.5f) {
            return sign * easeIn(progress / 0.5f) * 12f;
        } else {
            return -sign * (1f - easeOut((progress - 0.5f) / 0.5f)) * 12f;
        }
    }

    public boolean isRunning() {
        return timer != null && timer.isRunning();
    }

    private float easeIn(float t) {
        return t * t;
    }

    private float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }
}