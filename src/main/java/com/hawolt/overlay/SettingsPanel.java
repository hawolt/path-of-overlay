package com.hawolt.overlay;

import com.hawolt.Config;
import com.hawolt.Settings;
import com.hawolt.hotkey.HotkeyLabel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.function.Consumer;

public class SettingsPanel extends JPanel {

    private static final Color COL_TOGGLE_OFF_BORDER = new Color(150, 60, 60, 255);
    private static final Color COL_TOGGLE_OFF_TEXT = new Color(210, 100, 100, 255);
    private static final Color COL_TOGGLE_ON_BORDER = new Color(60, 150, 60, 255);
    private static final Color COL_TOGGLE_ON_TEXT = new Color(100, 210, 100, 255);
    private static final Color COL_SAVE_OK_BORDER = new Color(60, 160, 60, 255);
    private static final Color COL_ACTIVE_BORDER = new Color(180, 150, 60, 255);
    private static final Color COL_SAVE_OK_TEXT = new Color(100, 220, 100, 255);
    private static final Color COL_LOAD_OK_BORDER = new Color(60, 160, 60, 255);
    private static final Color COL_LOAD_OK_TEXT = new Color(100, 220, 100, 255);
    private static final Color COL_RECAL_OK_BORDER = new Color(60, 160, 60, 255);
    private static final Color COL_RECAL_OK_TEXT = new Color(100, 220, 100, 255);
    private static final Color BG_RECAL_OK = new Color(20, 60, 20, 220);
    private static final Color BG_TOGGLE_OFF = new Color(55, 20, 20, 220);
    private static final Color COL_LABEL = new Color(180, 168, 140, 210);
    private static final Color COL_VALUE = new Color(230, 215, 170, 255);
    private static final Color COL_TITLE = new Color(220, 200, 140, 255);
    private static final Color BG_TOGGLE_ON = new Color(20, 55, 20, 220);
    private static final Color BG_LISTEN = new Color(120, 100, 40, 130);
    private static final Color BG_SAVE_OK = new Color(20, 60, 20, 220);
    private static final Color BG_LOAD_OK = new Color(20, 60, 20, 220);
    private static final Color COL_BORDER = new Color(80, 65, 30, 200);
    private static final Color COL_CLOSE = new Color(160, 60, 40, 230);
    private static final Color COL_HINT = new Color(110, 100, 75, 180);
    private static final Color COL_GOLD = new Color(200, 168, 75, 255);
    private static final Color BG_OUTER = new Color(10, 8, 4, 255);
    private static final Color BG_SAVE = new Color(35, 28, 8, 220);
    private static final Color BG_FIELD = new Color(0, 0, 0, 130);
    private static final int SAVE_FEEDBACK_DURATION_MS = 1500;
    private static final int GRID_COLS = 2;
    private static final int GRID_ROWS = 3;
    private static final int ROW_H = 46;
    private static final int PAD = 24;
    private static final int GAP = 10;
    private static final int ARC = 10;
    private static final int W = 500;

    private static final String[] ROW_LABELS = {
            "Next Step", "Previous Step", "Move Overlay", "Toggle Timer", "Open Settings", "Pause/Unpause Timer"
    };

    private final Consumer<String> onPobLoad;
    private final Runnable onForceShowOverlay;
    private final Runnable onForceHideOverlay;
    private final Runnable onSuspendHotkeys;
    private final Runnable onResumeHotkeys;
    private final Runnable onRecalibrate;
    private final Settings settings;
    private final Runnable onNeedFocus;
    private final Runnable onSave;
    private final Runnable onClose;
    private final JTextField pobTextField;

    private final int[] fieldEncoded = new int[6];
    private int listeningIdx = -1;

    private boolean showSaveConfirmation = false;
    private boolean showLoadConfirmation = false;
    private boolean showRecalibrateConfirmation = false;
    private boolean overlayForceVisible = true;

    private String logPath = "";

    private final Rectangle[] fieldRects = new Rectangle[6];
    private Rectangle logBrowseRect = new Rectangle();
    private Rectangle recalibrateRect = new Rectangle();
    private Rectangle pobLoadRect = new Rectangle();
    private Rectangle forceShowRect = new Rectangle();
    private Rectangle closeRect = new Rectangle();
    private Rectangle saveRect = new Rectangle();
    private Rectangle logRect = new Rectangle();

    private Font titleFont;
    private Font labelFont;
    private Font valueFont;
    private Font hintFont;
    private Font closeBoldFont;

    public SettingsPanel(
            Settings settings,
            Runnable onSave,
            Runnable onClose,
            Runnable onForceShowOverlay,
            Runnable onForceHideOverlay,
            Runnable onRecalibrate,
            Runnable onNeedFocus,
            Runnable onSuspendHotkeys,
            Runnable onResumeHotkeys,
            Consumer<String> onPobLoad
    ) {
        this.settings = settings;
        this.onSave = onSave;
        this.onClose = onClose;
        this.onForceShowOverlay = onForceShowOverlay;
        this.onForceHideOverlay = onForceHideOverlay;
        this.onRecalibrate = onRecalibrate;
        this.onNeedFocus = onNeedFocus;
        this.onSuspendHotkeys = onSuspendHotkeys;
        this.onResumeHotkeys = onResumeHotkeys;
        this.onPobLoad = onPobLoad;
        setOpaque(false);
        setLayout(null);
        setFocusable(true);
        initFonts();
        loadValues();
        pobTextField = buildPobTextField();
        add(pobTextField);
        initListeners();
        setPreferredSize(new Dimension(W + PAD * 2, totalHeight()));
    }

    private JTextField buildPobTextField() {
        JTextField field = new JTextField();
        field.setFont(valueFont);
        field.setForeground(COL_VALUE);
        field.setBackground(new Color(0, 0, 0, 0));
        field.setOpaque(false);
        field.setCaretColor(COL_GOLD);
        field.setBorder(new EmptyBorder(0, 0, 0, 0));
        field.setSelectedTextColor(Color.BLACK);
        field.setSelectionColor(COL_GOLD);
        String saved = settings.getPobInput();
        if (saved != null) field.setText(saved);
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
        return field;
    }

    private void loadValues() {
        logPath = settings.getLogPath() != null ? settings.getLogPath() : "";
        fieldEncoded[0] = settings.getHotkeyNext();
        fieldEncoded[1] = settings.getHotkeyPrev();
        fieldEncoded[2] = settings.getHotkeyMove();
        fieldEncoded[3] = settings.getHotkeyTimer();
        fieldEncoded[4] = settings.getHotkeySettings();
        fieldEncoded[5] = settings.getHotkeyPause();
    }

    private void initFonts() {
        titleFont = new Font(Config.FONT_NAME, Font.BOLD, 19);
        labelFont = new Font(Config.FONT_NAME, Font.PLAIN, 15);
        valueFont = new Font(Config.FONT_NAME, Font.BOLD, 15);
        hintFont = new Font(Config.FONT_NAME, Font.PLAIN, 12);
        closeBoldFont = new Font(Config.FONT_NAME, Font.BOLD, 12);
    }

    private void positionPobTextField() {
        int pobRowY = PAD + 28 + GAP + ROW_H + GAP;
        int loadWidth = 80;
        int pobFieldWidth = W - loadWidth - GAP;
        FontMetrics labelMetrics = getFontMetrics(labelFont);
        int labelWidth = labelMetrics.stringWidth("POB") + 20;
        int fieldHeight = pobTextField.getPreferredSize().height;
        int textX = PAD + labelWidth;
        int textWidth = pobFieldWidth - labelWidth - 12;
        int textY = pobRowY + (ROW_H - fieldHeight) / 2;
        pobTextField.setBounds(textX, textY, textWidth, fieldHeight);
    }

    private void initListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Point p = e.getPoint();

                if (closeRect.contains(p)) {
                    stopListening();
                    onClose.run();
                    return;
                }
                if (saveRect.contains(p)) {
                    stopListening();
                    doSave();
                    return;
                }
                if (pobLoadRect.contains(p)) {
                    stopListening();
                    doLoad();
                    return;
                }
                if (logBrowseRect.contains(p)) {
                    stopListening();
                    browseForLogFile();
                    return;
                }
                if (forceShowRect.contains(p)) {
                    stopListening();
                    overlayForceVisible = !overlayForceVisible;
                    if (overlayForceVisible) onForceShowOverlay.run();
                    else onForceHideOverlay.run();
                    repaint();
                    return;
                }
                if (recalibrateRect.contains(p)) {
                    stopListening();
                    onRecalibrate.run();
                    showRecalibrateConfirmation = true;
                    repaint();
                    Timer resetTimer = new Timer(SAVE_FEEDBACK_DURATION_MS, ev -> {
                        showRecalibrateConfirmation = false;
                        repaint();
                    });
                    resetTimer.setRepeats(false);
                    resetTimer.start();
                    return;
                }

                if (listeningIdx >= 0) return;

                if (e.getButton() == MouseEvent.BUTTON1) {
                    for (int i = 0; i < fieldRects.length; i++) {
                        if (fieldRects[i] != null && fieldRects[i].contains(p)) {
                            startListening(i);
                            return;
                        }
                    }
                }

                stopListening();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (listeningIdx < 0) return;
                Point p = e.getPoint();

                if (e.getButton() == MouseEvent.BUTTON1
                        && fieldRects[listeningIdx] != null
                        && fieldRects[listeningIdx].contains(p)) {
                    return;
                }

                int vk = HotkeyLabel.mouseButtonVk(e.getButton());
                if (vk != 0) {
                    int mods = e.getModifiersEx() & (
                            InputEvent.SHIFT_DOWN_MASK
                                    | InputEvent.CTRL_DOWN_MASK
                                    | InputEvent.ALT_DOWN_MASK
                    );
                    commitHotkey(HotkeyLabel.encode(vk, mods));
                }
            }
        });

        addMouseWheelListener(e -> {
            if (listeningIdx < 0) return;
            int vk = e.getWheelRotation() < 0
                    ? HotkeyLabel.VK_SCROLL_UP
                    : HotkeyLabel.VK_SCROLL_DOWN;
            commitHotkey(HotkeyLabel.encode(vk, 0));
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (listeningIdx < 0) return;
                int vk = e.getKeyCode();
                if (vk == KeyEvent.VK_ESCAPE) {
                    stopListening();
                    return;
                }
                if (vk == KeyEvent.VK_SHIFT
                        || vk == KeyEvent.VK_CONTROL
                        || vk == KeyEvent.VK_ALT) return;
                int mods = e.getModifiersEx() & (
                        InputEvent.SHIFT_DOWN_MASK
                                | InputEvent.CTRL_DOWN_MASK
                                | InputEvent.ALT_DOWN_MASK
                );
                commitHotkey(HotkeyLabel.encode(vk, mods));
            }
        });
    }

    private void browseForLogFile() {
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        try {
            if (parentWindow != null) parentWindow.setAlwaysOnTop(false);
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Client.txt");
            File current = new File(logPath);
            if (current.exists()) {
                chooser.setCurrentDirectory(
                        current.isDirectory() ? current : current.getParentFile()
                );
                chooser.setSelectedFile(current);
            }
            int result = chooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                logPath = chooser.getSelectedFile().getAbsolutePath();
                repaint();
            }
        } finally {
            if (parentWindow != null) {
                parentWindow.setAlwaysOnTop(true);
                parentWindow.toFront();
            }
        }
    }

    private void startListening(int idx) {
        listeningIdx = idx;
        onSuspendHotkeys.run();
        repaint();
        onNeedFocus.run();
        requestFocusInWindow();
    }

    private void stopListening() {
        if (listeningIdx < 0) return;
        listeningIdx = -1;
        onResumeHotkeys.run();
        repaint();
    }

    private void commitHotkey(int encoded) {
        if (listeningIdx < 0) return;
        for (int i = 0; i < fieldEncoded.length; i++) {
            if (i != listeningIdx && fieldEncoded[i] == encoded) {
                fieldEncoded[i] = 0;
            }
        }
        fieldEncoded[listeningIdx] = encoded;
        stopListening();
    }

    private void doSave() {
        settings.setLogPath(logPath);
        settings.setPobInput(pobTextField.getText());
        settings.setHotkeyNext(fieldEncoded[0]);
        settings.setHotkeyPrev(fieldEncoded[1]);
        settings.setHotkeyMove(fieldEncoded[2]);
        settings.setHotkeyTimer(fieldEncoded[3]);
        settings.setHotkeySettings(fieldEncoded[4]);
        settings.setHotkeyPause(fieldEncoded[5]);
        settings.save();
        onSave.run();
        showSaveConfirmation = true;
        repaint();
        Timer resetTimer = new Timer(SAVE_FEEDBACK_DURATION_MS, e -> {
            showSaveConfirmation = false;
            repaint();
        });
        resetTimer.setRepeats(false);
        resetTimer.start();
    }

    private void doLoad() {
        String pobText = pobTextField.getText();
        settings.setPobInput(pobText);
        settings.save();
        onPobLoad.accept(pobText);
        showLoadConfirmation = true;
        repaint();
        Timer resetTimer = new Timer(SAVE_FEEDBACK_DURATION_MS, e -> {
            showLoadConfirmation = false;
            repaint();
        });
        resetTimer.setRepeats(false);
        resetTimer.start();
    }

    private int totalHeight() {
        int gridRowCount = (int) Math.ceil((double) ROW_LABELS.length / GRID_COLS);
        return PAD + 28 + GAP
                + ROW_H + GAP
                + ROW_H + GAP
                + 18 + GAP
                + (ROW_H + GAP) * gridRowCount + GAP
                + ROW_H + GAP
                + ROW_H + PAD;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        positionPobTextField();

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB
        );

        int x = PAD, y = PAD, w = W;

        g.setColor(BG_OUTER);
        g.fillRoundRect(0, 0, W + PAD * 2, totalHeight(), ARC * 2, ARC * 2);

        g.setFont(titleFont);
        g.setColor(COL_TITLE);
        g.drawString("Settings", x, y + 20);

        int cx = x + w - 22, cy = y;
        closeRect = new Rectangle(cx, cy, 22, 22);
        g.setColor(COL_CLOSE);
        g.fillRoundRect(cx, cy, 22, 22, 6, 6);
        g.setFont(closeBoldFont);
        g.setColor(Color.WHITE);
        FontMetrics fontMetrics = g.getFontMetrics();
        g.drawString(
                "X",
                cx + (22 - fontMetrics.stringWidth("X")) / 2,
                cy + 15
        );

        y += 28 + GAP;

        int browseWidth = 80;
        int fieldWidth = w - browseWidth - GAP;

        paintField(g, x, y, fieldWidth, "Log File", logPath, false, false);
        logRect = new Rectangle(x, y, fieldWidth, ROW_H);

        g.setColor(BG_SAVE);
        g.fillRoundRect(x + fieldWidth + GAP, y, browseWidth, ROW_H, ARC, ARC);
        g.setColor(COL_BORDER);
        g.drawRoundRect(x + fieldWidth + GAP, y, browseWidth, ROW_H, ARC, ARC);
        g.setFont(valueFont);
        g.setColor(COL_GOLD);
        fontMetrics = g.getFontMetrics();
        String browseText = "Browse...";
        g.drawString(
                browseText,
                x + fieldWidth + GAP + (browseWidth - fontMetrics.stringWidth(browseText)) / 2,
                y + (ROW_H + fontMetrics.getAscent() - fontMetrics.getDescent()) / 2
        );
        logBrowseRect = new Rectangle(x + fieldWidth + GAP, y, browseWidth, ROW_H);

        y += ROW_H + GAP;

        int loadWidth = 80;
        int pobFieldWidth = w - loadWidth - GAP;
        boolean pobFocused = pobTextField.hasFocus();

        paintFieldBackground(g, x, y, pobFieldWidth, "POB", pobFocused);

        g.setColor(showLoadConfirmation ? BG_LOAD_OK : BG_SAVE);
        g.fillRoundRect(x + pobFieldWidth + GAP, y, loadWidth, ROW_H, ARC, ARC);
        g.setColor(showLoadConfirmation ? COL_LOAD_OK_BORDER : COL_BORDER);
        g.drawRoundRect(x + pobFieldWidth + GAP, y, loadWidth, ROW_H, ARC, ARC);
        g.setFont(valueFont);
        g.setColor(showLoadConfirmation ? COL_LOAD_OK_TEXT : COL_GOLD);
        fontMetrics = g.getFontMetrics();
        String loadText = showLoadConfirmation ? "Loaded!" : "Load";
        g.drawString(
                loadText,
                x + pobFieldWidth + GAP + (loadWidth - fontMetrics.stringWidth(loadText)) / 2,
                y + (ROW_H + fontMetrics.getAscent() - fontMetrics.getDescent()) / 2
        );
        pobLoadRect = new Rectangle(x + pobFieldWidth + GAP, y, loadWidth, ROW_H);

        y += ROW_H + GAP;

        g.setFont(hintFont);
        g.setColor(COL_HINT);
        g.drawString("Click a hotkey field, then press any key, mouse button, or scroll.", x, y + 13);
        y += 18 + GAP;

        int cellWidth = (w - GAP) / GRID_COLS;
        for (int i = 0; i < ROW_LABELS.length; i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int cellX = x + col * (cellWidth + GAP);
            int cellY = y + row * (ROW_H + GAP);
            boolean listening = (listeningIdx == i);
            String display = listening ? "..."
                    : fieldEncoded[i] == 0 ? "-"
                    : HotkeyLabel.labelOf(fieldEncoded[i]);
            paintField(g, cellX, cellY, cellWidth, ROW_LABELS[i], display, listening, true);
            fieldRects[i] = new Rectangle(cellX, cellY, cellWidth, ROW_H);
        }

        int gridRowCount = (int) Math.ceil((double) ROW_LABELS.length / GRID_COLS);
        y += gridRowCount * (ROW_H + GAP);

        int halfWidth = (w - GAP) / 2;

        forceShowRect = new Rectangle(x, y, halfWidth, ROW_H);
        g.setColor(overlayForceVisible ? BG_TOGGLE_ON : BG_TOGGLE_OFF);
        g.fillRoundRect(x, y, halfWidth, ROW_H, ARC, ARC);
        g.setColor(overlayForceVisible ? COL_TOGGLE_ON_BORDER : COL_TOGGLE_OFF_BORDER);
        g.drawRoundRect(x, y, halfWidth, ROW_H, ARC, ARC);
        g.setFont(valueFont);
        g.setColor(overlayForceVisible ? COL_TOGGLE_ON_TEXT : COL_TOGGLE_OFF_TEXT);
        fontMetrics = g.getFontMetrics();
        String toggleText = overlayForceVisible ? "Overlay: Shown" : "Overlay: Hidden";
        g.drawString(
                toggleText,
                x + (halfWidth - fontMetrics.stringWidth(toggleText)) / 2,
                y + (ROW_H + fontMetrics.getAscent() - fontMetrics.getDescent()) / 2
        );

        int recalX = x + halfWidth + GAP;
        recalibrateRect = new Rectangle(recalX, y, halfWidth, ROW_H);
        g.setColor(showRecalibrateConfirmation ? BG_RECAL_OK : BG_SAVE);
        g.fillRoundRect(recalX, y, halfWidth, ROW_H, ARC, ARC);
        g.setColor(showRecalibrateConfirmation ? COL_RECAL_OK_BORDER : COL_BORDER);
        g.drawRoundRect(recalX, y, halfWidth, ROW_H, ARC, ARC);
        g.setFont(valueFont);
        g.setColor(showRecalibrateConfirmation ? COL_RECAL_OK_TEXT : COL_GOLD);
        fontMetrics = g.getFontMetrics();
        String recalibrateText = showRecalibrateConfirmation ? "Done!" : "Recalibrate";
        g.drawString(
                recalibrateText,
                recalX + (halfWidth - fontMetrics.stringWidth(recalibrateText)) / 2,
                y + (ROW_H + fontMetrics.getAscent() - fontMetrics.getDescent()) / 2
        );

        y += ROW_H + GAP;

        saveRect = new Rectangle(x, y, w, ROW_H);
        g.setColor(showSaveConfirmation ? BG_SAVE_OK : BG_SAVE);
        g.fillRoundRect(x, y, w, ROW_H, ARC, ARC);
        g.setColor(showSaveConfirmation ? COL_SAVE_OK_BORDER : COL_BORDER);
        g.drawRoundRect(x, y, w, ROW_H, ARC, ARC);
        g.setFont(valueFont);
        g.setColor(showSaveConfirmation ? COL_SAVE_OK_TEXT : COL_GOLD);
        fontMetrics = g.getFontMetrics();
        String saveText = showSaveConfirmation ? "Saved!" : "Save";
        g.drawString(
                saveText,
                x + (w - fontMetrics.stringWidth(saveText)) / 2,
                y + (ROW_H + fontMetrics.getAscent() - fontMetrics.getDescent()) / 2
        );

        g.dispose();
    }

    private void paintFieldBackground(
            Graphics2D g,
            int x,
            int y,
            int w,
            String label,
            boolean active
    ) {
        g.setColor(active ? BG_LISTEN : BG_FIELD);
        g.fillRoundRect(x, y, w, ROW_H, ARC, ARC);
        g.setColor(active ? COL_ACTIVE_BORDER : COL_BORDER);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, y, w, ROW_H, ARC, ARC);
        g.setFont(labelFont);
        g.setColor(COL_LABEL);
        FontMetrics labelMetrics = g.getFontMetrics();
        int baseline = y + (ROW_H + labelMetrics.getAscent() - labelMetrics.getDescent()) / 2;
        g.drawString(label, x + 12, baseline);
    }

    private int paintField(
            Graphics2D g,
            int x,
            int y,
            int w,
            String label,
            String value,
            boolean active,
            boolean rightAlign
    ) {
        g.setColor(active ? BG_LISTEN : BG_FIELD);
        g.fillRoundRect(x, y, w, ROW_H, ARC, ARC);
        g.setColor(active ? COL_ACTIVE_BORDER : COL_BORDER);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, y, w, ROW_H, ARC, ARC);

        g.setFont(labelFont);
        g.setColor(COL_LABEL);
        FontMetrics labelMetrics = g.getFontMetrics();
        int baseline = y + (ROW_H + labelMetrics.getAscent() - labelMetrics.getDescent()) / 2;
        g.drawString(label, x + 12, baseline);

        g.setFont(valueFont);
        g.setColor(active ? COL_GOLD : COL_VALUE);
        FontMetrics valueMetrics = g.getFontMetrics();
        int valueBaseline = y + (ROW_H + valueMetrics.getAscent() - valueMetrics.getDescent()) / 2;

        if (rightAlign) {
            int valueX = x + w - valueMetrics.stringWidth(value) - 12;
            g.drawString(value, valueX, valueBaseline);
        } else {
            int labelWidth = labelMetrics.stringWidth(label) + 20;
            int availableWidth = w - labelWidth - 12;
            String truncated = truncateToFit(value, valueMetrics, availableWidth);
            g.drawString(truncated, x + labelWidth, valueBaseline);
        }

        return y + ROW_H + GAP;
    }

    private String truncateToFit(String text, FontMetrics metrics, int maxWidth) {
        if (metrics.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        for (int i = text.length() - 1; i >= 0; i--) {
            String candidate = ellipsis + text.substring(text.length() - i);
            if (metrics.stringWidth(candidate) <= maxWidth) return candidate;
        }
        return ellipsis;
    }
}