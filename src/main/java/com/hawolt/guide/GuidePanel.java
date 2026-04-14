package com.hawolt.guide;

import com.hawolt.Config;
import com.hawolt.data.GemRequirements;
import com.hawolt.data.MappingConfig;
import com.hawolt.data.RewardsConfig;
import com.hawolt.guide.GemAnnotator.DisplayEntry;
import com.hawolt.guide.model.GuideStep;
import com.hawolt.logger.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GuidePanel extends JPanel {

    private static final int TIMER_GAP = 16;
    private static final int PADDING = 14;
    private static final int ARC = 14;
    private static final int GAP = 8;

    private static final Color MOVE_MODE_BACKGROUND = new Color(255, 200, 0, 30);
    private static final Color MOVE_MODE_BORDER = new Color(255, 200, 0, 180);
    private static final Color TIMER_BG_COLOR = new Color(0, 0, 0, Config.BG_ALPHA_STEP_0);
    private static final Color TIMER_ACT_COLOR = new Color(255, 255, 255, Config.TEXT_ALPHA_STEP_0);
    private static final Color TIMER_CLOCK_COLOR = new Color(255, 255, 255, Config.TEXT_ALPHA_STEP_0);

    private final float[] slotTextAlphas = {
            Config.TEXT_ALPHA_STEP_0 / 255f,
            Config.TEXT_ALPHA_STEP_1 / 255f,
            Config.TEXT_ALPHA_STEP_2 / 255f
    };
    private final int[] slotBackgroundAlphas = {
            Config.BG_ALPHA_STEP_0,
            Config.BG_ALPHA_STEP_1,
            Config.BG_ALPHA_STEP_2
    };

    private final SlideAnimator animator = new SlideAnimator(this);
    private final Font[] slotFonts = new Font[Config.VISIBLE_STEPS];
    private final MappingConfig mappingConfig;
    private final RewardsConfig rewardsConfig;
    private final GemAnnotator gemAnnotator;
    private final Font timerClockFont;
    private final int timerRowHeight;

    private GemRequirements gemRequirements = GemRequirements.empty();
    private List<DisplayEntry> displaySteps = new ArrayList<>();
    private List<GuideStep> rawSteps = new ArrayList<>();
    private Consumer<Dimension> onSizeNeeded;
    private Runnable onResizeToContent;
    private String activeBandit = "Kill all";
    private ActTimer actTimer;
    private int stepIndex = 0;
    private boolean timerVisibleBeforeMoveMode = false;
    private boolean timerVisible = false;
    private boolean moveMode = false;

    public GuidePanel() {
        setOpaque(false);
        mappingConfig = MappingConfig.load();
        rewardsConfig = RewardsConfig.load();
        gemAnnotator = new GemAnnotator(mappingConfig, rewardsConfig);
        loadFonts();
        timerClockFont = resolveMonoBold(Config.TIMER_CLOCK_FONT_SIZE);
        timerRowHeight = computeTimerRowHeight();
        loadSteps();

        new Timer(500, event -> {
            if (timerVisible && actTimer != null && actTimer.getCurrentAct() != null) repaint();
        }).start();
    }

    public void setActTimer(ActTimer actTimer) {
        this.actTimer = actTimer;
    }

    public void setOnSizeNeeded(Consumer<Dimension> sizeConsumer, Runnable resizeToContent) {
        this.onSizeNeeded = sizeConsumer;
        this.onResizeToContent = resizeToContent;
    }

    public void setBandit(String bandit) {
        this.activeBandit = bandit;
        rebuildDisplaySteps();
    }

    public void setActiveClass(String className) {
        rewardsConfig.setActiveClass(className);
        Logger.info("[GuidePanel] Active class updated to: {}", className);
        rebuildDisplaySteps();
    }

    public void setGemRequirements(GemRequirements requirements) {
        this.gemRequirements = requirements;
        rebuildDisplaySteps();
    }

    private void rebuildDisplaySteps() {
        int anchorRawIndex = displaySteps.isEmpty() ? 0 : displaySteps.get(stepIndex).rawIndex();
        displaySteps = gemAnnotator.buildDisplayList(rawSteps, gemRequirements, activeBandit);

        int newStepIndex = 0;
        for (int i = 0; i < displaySteps.size(); i++) {
            if (displaySteps.get(i).rawIndex() == anchorRawIndex) {
                newStepIndex = i;
                break;
            }
        }
        stepIndex = Math.min(newStepIndex, Math.max(0, displaySteps.size() - 1));

        if (onResizeToContent != null) onResizeToContent.run();
        repaint();
    }

    public void toggleTimerVisible() {
        timerVisible = !timerVisible;
        repaint();
    }

    public void next() {
        if (stepIndex >= displaySteps.size() - 1) return;
        int nextIndex = stepIndex + 1;
        Dimension currentSize = computeRequiredSizeForIndex(stepIndex);
        Dimension nextSize = computeRequiredSizeForIndex(nextIndex);
        boolean nextIsSmaller = nextSize.width < currentSize.width || nextSize.height < currentSize.height;
        animator.play(
                SlideAnimator.Direction.FORWARD,
                () -> {
                    if (!nextIsSmaller && onSizeNeeded != null) onSizeNeeded.accept(nextSize);
                },
                () -> {
                    stepIndex = nextIndex;
                    if (nextIsSmaller && onSizeNeeded != null) onSizeNeeded.accept(nextSize);
                }
        );
    }

    public void prev() {
        if (stepIndex <= 0) return;
        int prevIndex = stepIndex - 1;
        Dimension currentSize = computeRequiredSizeForIndex(stepIndex);
        Dimension prevSize = computeRequiredSizeForIndex(prevIndex);
        boolean prevIsSmaller = prevSize.width < currentSize.width || prevSize.height < currentSize.height;
        animator.play(
                SlideAnimator.Direction.BACKWARD,
                () -> {
                    if (!prevIsSmaller && onSizeNeeded != null) onSizeNeeded.accept(prevSize);
                },
                () -> {
                    stepIndex = prevIndex;
                    if (prevIsSmaller && onSizeNeeded != null) onSizeNeeded.accept(prevSize);
                }
        );
    }

    public void seekToZone(String zoneName, int areaLevel) {
        updateActTimer(zoneName, areaLevel);
        String lowerZone = zoneName.toLowerCase();
        int scanLimit = Math.min(stepIndex + Config.ZONE_SEEK_LOOKAHEAD, displaySteps.size());
        for (int index = stepIndex; index < scanLimit; index++) {
            GuideStep step = displaySteps.get(index).step();
            String plainText = step.toPlainText().toLowerCase();
            if (matchesZoneStep(plainText, lowerZone, step.getZoneLevel(), areaLevel)) {
                applySeek(index + 1);
                return;
            }
            if (index > stepIndex && isZoneStep(plainText)) {
                Logger.debug("[GuidePanel] seekToZone aborted: encountered unvisited zone step at index {}", index);
                return;
            }
        }
    }

    public void seekToZoneFromStart(String zoneName, int areaLevel) {
        updateActTimer(zoneName, areaLevel);
        String lowerZone = zoneName.toLowerCase();
        for (int index = 0; index < displaySteps.size(); index++) {
            GuideStep step = displaySteps.get(index).step();
            if (matchesZoneStep(step.toPlainText().toLowerCase(), lowerZone, step.getZoneLevel(), areaLevel)) {
                applySeek(index + 1);
                return;
            }
        }
    }

    public void setMoveMode(boolean enabled) {
        moveMode = enabled;
        if (enabled) {
            timerVisibleBeforeMoveMode = timerVisible;
            timerVisible = false;
        } else {
            timerVisible = timerVisibleBeforeMoveMode;
        }
        repaint();
    }

    public void toggleTimerPause() {
        if (actTimer != null) actTimer.togglePause();
    }

    public boolean isMoveMode() {
        return moveMode;
    }

    public Dimension computeRequiredSize() {
        return computeRequiredSizeForIndex(stepIndex);
    }

    public void reloadSteps() {
        try {
            GuideParser parser = new GuideParser(mappingConfig);
            rawSteps = parser.load("road.map");
            displaySteps = gemAnnotator.buildDisplayList(rawSteps, gemRequirements, activeBandit);
            stepIndex = Math.min(stepIndex, Math.max(0, displaySteps.size() - 1));
            Logger.info("[GuidePanel] Reloaded {} steps.", displaySteps.size());
            if (onResizeToContent != null) onResizeToContent.run();
            repaint();
        } catch (IOException exception) {
            Logger.error("[GuidePanel] Failed to reload road.map: {}", exception.getMessage());
        }
    }

    private boolean isZoneStep(String plainText) {
        return plainText.contains("enter") || plainText.contains("to travel to");
    }

    private void updateActTimer(String zoneName, int areaLevel) {
        if (actTimer != null) {
            actTimer.onZoneEntered(zoneName, areaLevel);
            Logger.info("[GuidePanel] seekToZone={} currentAct={}", zoneName, actTimer.getCurrentAct());
            if (actTimer.getCurrentAct() == ActTimer.Act.ACT_1) {
                Logger.debug("[GuidePanel] ACT_1 detected, setting timerVisible=true");
                SwingUtilities.invokeLater(() -> {
                    timerVisible = true;
                    repaint();
                });
            }
        }
    }

    private boolean matchesZoneStep(String plainText, String lowerZone, int stepLevel, int areaLevel) {
        if (!plainText.contains(lowerZone) || !isZoneStep(plainText)) return false;
        if (stepLevel != 0 && areaLevel != 0) return stepLevel == areaLevel;
        return true;
    }

    private void applySeek(int targetIndex) {
        if (targetIndex >= displaySteps.size()) return;
        SwingUtilities.invokeLater(() -> {
            Dimension currentSize = computeRequiredSizeForIndex(stepIndex);
            Dimension targetSize = computeRequiredSizeForIndex(targetIndex);
            boolean targetIsSmaller = targetSize.width < currentSize.width || targetSize.height < currentSize.height;
            animator.play(
                    SlideAnimator.Direction.FORWARD,
                    () -> {
                        if (!targetIsSmaller && onSizeNeeded != null) onSizeNeeded.accept(targetSize);
                    },
                    () -> {
                        stepIndex = targetIndex;
                        if (targetIsSmaller && onSizeNeeded != null) onSizeNeeded.accept(targetSize);
                    }
            );
        });
    }

    private Dimension computeRequiredSizeForIndex(int targetStepIndex) {
        int totalWidth = timerMinWidth();
        int totalHeight = 0;

        for (int slot = 0; slot < Config.VISIBLE_STEPS; slot++) {
            int index = targetStepIndex + slot;
            if (index >= displaySteps.size()) break;
            GuideRenderer.Layout layout = GuideRenderer.measure(
                    displaySteps.get(index).step(),
                    slotFonts[slot]
            );
            int boxWidth = layout.contentWidth() + PADDING * 2;
            int boxHeight = layout.contentHeight() + PADDING * 2;
            if (boxWidth > totalWidth) totalWidth = boxWidth;
            totalHeight += boxHeight + GAP;
        }

        totalHeight = totalHeight - GAP + PADDING * 2;
        int timerBlockHeight = timerRowHeight + PADDING + TIMER_GAP;
        return new Dimension(totalWidth + PADDING * 2 + 2, totalHeight + timerBlockHeight + 2);
    }

    private void loadSteps() {
        try {
            GuideParser parser = new GuideParser(mappingConfig);
            rawSteps = parser.load("road.map");
            displaySteps = gemAnnotator.buildDisplayList(rawSteps, gemRequirements, activeBandit);
            Logger.info("[GuidePanel] Loaded {} steps.", displaySteps.size());
        } catch (IOException exception) {
            Logger.error("[GuidePanel] Failed to load road.map: {}", exception.getMessage());
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB
        );

        int timerBlockHeight = timerRowHeight + PADDING + TIMER_GAP;
        int currentY = timerBlockHeight;

        float animAlpha = animator.isRunning() ? animator.alpha() : 1f;
        float animSlideY = animator.isRunning() ? animator.slideY() : 0f;

        paintTimerRow(g2);

        g2.translate(0, (int) animSlideY);

        if (displaySteps.isEmpty()) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, animAlpha));
            g2.setColor(new Color(0, 0, 0, Config.BG_ALPHA_STEP_0));
            g2.fillRoundRect(0, currentY, 300, 50, ARC, ARC);
            g2.setFont(slotFonts[0]);
            g2.setColor(Color.WHITE);
            g2.drawString("No steps loaded.", PADDING, currentY + 30);
            g2.dispose();
            return;
        }

        record SlotData(GuideRenderer.Layout layout, int boxWidth, int boxHeight) {
        }
        List<SlotData> slotDataList = new ArrayList<>();
        int maxBoxWidth = 0;
        for (int slot = 0; slot < Config.VISIBLE_STEPS; slot++) {
            int index = stepIndex + slot;
            if (index >= displaySteps.size()) break;
            GuideRenderer.Layout layout = GuideRenderer.measure(
                    displaySteps.get(index).step(),
                    slotFonts[slot]
            );
            int boxWidth = layout.contentWidth() + PADDING * 2;
            int boxHeight = layout.contentHeight() + PADDING * 2;
            if (boxWidth > maxBoxWidth) maxBoxWidth = boxWidth;
            slotDataList.add(new SlotData(layout, boxWidth, boxHeight));
        }

        if (moveMode) {
            int borderWidth = getWidth() - 2;
            int borderHeight = getHeight() - 2;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, animAlpha));
            g2.setColor(MOVE_MODE_BACKGROUND);
            g2.fillRoundRect(1, 1, borderWidth, borderHeight, ARC, ARC);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            g2.setColor(MOVE_MODE_BORDER);
            g2.setStroke(new BasicStroke(
                    1.5f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND,
                    0,
                    new float[]{4, 4},
                    0
            ));
            g2.drawRoundRect(1, 1, borderWidth, borderHeight, ARC, ARC);
            g2.setStroke(new BasicStroke(1f));
        }

        for (int slot = 0; slot < slotDataList.size(); slot++) {
            SlotData slotData = slotDataList.get(slot);
            float finalAlpha = slotTextAlphas[slot] * animAlpha;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, finalAlpha));
            if (!moveMode) {
                g2.setColor(new Color(0, 0, 0, slotBackgroundAlphas[slot]));
                g2.fillRoundRect(0, currentY, maxBoxWidth, slotData.boxHeight(), ARC, ARC);
            }
            GuideRenderer.render(g2, slotData.layout(), PADDING, currentY + PADDING, slotFonts[slot]);
            currentY += slotData.boxHeight() + GAP;
        }

        g2.dispose();
    }

    private void paintTimerRow(Graphics2D g2) {
        if (!timerVisible || actTimer == null) return;
        ActTimer.Act currentAct = actTimer.getCurrentAct();
        Duration elapsed = actTimer.getCurrentElapsed();
        if (currentAct == null || elapsed == null) return;

        Font timerActFont = resolveFontin(Config.TIMER_ACT_FONT_SIZE);
        FontMetrics actMetrics = g2.getFontMetrics(timerActFont);
        FontMetrics clockMetrics = g2.getFontMetrics(timerClockFont);

        String actLabel = "Act " + currentAct.roman;
        String timeLabel = ActTimer.formatDuration(elapsed);

        int actWidth = actMetrics.stringWidth(actLabel);
        int clockWidth = clockMetrics.stringWidth(timeLabel);
        int totalWidth = actWidth + clockWidth + PADDING * 3;
        int rowHeight = timerRowHeight;

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        g2.setColor(TIMER_BG_COLOR);
        g2.fillRoundRect(0, 0, totalWidth, rowHeight, ARC, ARC);

        int baseline = (rowHeight + actMetrics.getAscent() - actMetrics.getDescent()) / 2;
        g2.setFont(timerActFont);
        g2.setColor(TIMER_ACT_COLOR);
        g2.drawString(actLabel, PADDING, baseline);

        g2.setFont(timerClockFont);
        g2.setColor(TIMER_CLOCK_COLOR);
        g2.drawString(timeLabel, PADDING * 2 + actWidth, baseline);
    }

    private int computeTimerRowHeight() {
        Font font = resolveFontin(Config.TIMER_ACT_FONT_SIZE);
        BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        FontMetrics metrics = dummy.createGraphics().getFontMetrics(font);
        return metrics.getHeight() + PADDING;
    }

    private int timerMinWidth() {
        Font actFont = resolveFontin(Config.TIMER_ACT_FONT_SIZE);
        BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        FontMetrics actMetrics = dummy.createGraphics().getFontMetrics(actFont);
        FontMetrics clockMetrics = dummy.createGraphics().getFontMetrics(timerClockFont);
        return actMetrics.stringWidth("Act X") + clockMetrics.stringWidth("00:00:00") + PADDING * 3;
    }

    private void loadFonts() {
        Font baseFont = resolveFontin(Config.FONT_SIZE_STEP_0);
        int maxSlot = Config.VISIBLE_STEPS - 1;
        float sizeFirst = Config.FONT_SIZE_STEP_0;
        float sizeLast = Config.FONT_SIZE_STEP_2;
        for (int slot = 0; slot < Config.VISIBLE_STEPS; slot++) {
            float t = maxSlot == 0 ? 0f : (float) slot / maxSlot;
            float size = sizeFirst + t * (sizeLast - sizeFirst);
            slotFonts[slot] = baseFont.deriveFont(Font.PLAIN, size);
        }
    }

    private Font resolveFontin(int size) {
        try {
            InputStream stream = getClass().getClassLoader().getResourceAsStream("fonts/Fontin-Regular.ttf");
            if (stream != null) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, stream);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                return font.deriveFont(Font.PLAIN, (float) size);
            }
        } catch (Exception ignored) {
        }
        return new Font(Config.FONT_NAME, Font.PLAIN, size);
    }

    private Font resolveMonoBold(int size) {
        return new Font(Font.MONOSPACED, Font.BOLD, size);
    }
}