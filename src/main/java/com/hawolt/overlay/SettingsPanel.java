package com.hawolt.overlay;

import com.hawolt.Config;
import com.hawolt.Settings;
import com.hawolt.hotkey.HotkeyLabel;
import com.hawolt.logger.Logger;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.hawolt.overlay.SettingsPainter.*;

public class SettingsPanel extends JPanel {

    private static final String[] BANDIT_OPTIONS = {"Kill all", "Help Oak", "Help Kraityn", "Help Alira"};
    private static final String[] ROW_LABELS = {
            "Next Step", "Previous Step", "Move Overlay", "Toggle Timer", "Open Settings", "Pause/Unpause Timer"
    };

    private static final int SAVE_FEEDBACK_DURATION_MS = 1500;
    private static final int GRID_COLS = 2;

    private final SettingsHotkeyHandler hotkeyHandler;
    private final Rectangle[] fieldRects = new Rectangle[6];
    private final int[] fieldEncoded = new int[6];
    private final Consumer<String> onGuideFileChanged;
    private final Consumer<String> onPobLoad;
    private final Runnable onForceShowOverlay;
    private final Runnable onForceHideOverlay;
    private final Runnable onSuspendHotkeys;
    private final Runnable onResumeHotkeys;
    private final Runnable onRecalibrate;
    private final JTextField pobTextField;
    private final Runnable onNeedFocus;
    private final Runnable onPobClear;
    private final Settings settings;
    private final Runnable onClose;
    private final Runnable onSave;

    private List<String> loadoutKeys = new ArrayList<>();
    private JSONObject loadouts = new JSONObject();
    private Rectangle guideEditRect = new Rectangle();
    private Rectangle guideBrowseRect = new Rectangle();
    private Rectangle guideClearRect = new Rectangle();
    private Rectangle logBrowseRect = new Rectangle();
    private Rectangle recalibrateRect = new Rectangle();
    private Rectangle forceShowRect = new Rectangle();
    private Rectangle pobClearRect = new Rectangle();
    private Rectangle pobLoadRect = new Rectangle();
    private Rectangle loadoutRect = new Rectangle();
    private Rectangle banditRect = new Rectangle();
    private Rectangle closeRect = new Rectangle();
    private Rectangle logRect = new Rectangle();

    private Font closeBoldFont;
    private Font titleFont;
    private Font labelFont;
    private Font valueFont;
    private Font hintFont;

    private boolean showRecalibrateConfirmation = false;
    private boolean showLoadConfirmation = false;
    private boolean overlayForceVisible = true;

    private String guidePath = "";
    private String logPath = "";
    private int loadoutIndex = 0;
    private int banditIndex = 0;

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
            Runnable onPobClear,
            Consumer<String> onPobLoad,
            Consumer<String> onGuideFileChanged
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
        this.onPobClear = onPobClear;
        this.onPobLoad = onPobLoad;
        this.onGuideFileChanged = onGuideFileChanged;
        setOpaque(false);
        setLayout(null);
        setFocusable(true);
        initFonts();
        loadValues();
        pobTextField = buildPobTextField();
        add(pobTextField);
        hotkeyHandler = new SettingsHotkeyHandler(
                fieldEncoded,
                onSuspendHotkeys,
                onResumeHotkeys,
                () -> {
                    onNeedFocus.run();
                    requestFocusInWindow();
                },
                this::repaint,
                index -> doSave()
        );
        hotkeyHandler.install(this);
        initMouseListener();
        setPreferredSize(new Dimension(W + PAD * 2, totalHeight()));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        revalidate();
    }

    @Override
    public void doLayout() {
        super.doLayout();
        positionPobTextField();
    }

    public void setLoadouts(JSONObject newLoadouts, String savedLoadout) {
        this.loadouts = newLoadouts;
        this.loadoutKeys = new ArrayList<>(newLoadouts.keySet());
        this.loadoutIndex = 0;
        for (int i = 0; i < loadoutKeys.size(); i++) {
            if (loadoutKeys.get(i).equals(savedLoadout)) {
                loadoutIndex = i;
                break;
            }
        }
        Logger.info(
                "[SettingsPanel] Loadouts set: keys={} selected={}",
                loadoutKeys,
                loadoutKeys.isEmpty() ? "none" : loadoutKeys.get(loadoutIndex)
        );
        repaint();
    }

    public void setBandit(String bandit) {
        for (int i = 0; i < BANDIT_OPTIONS.length; i++) {
            if (BANDIT_OPTIONS[i].equalsIgnoreCase(bandit)) {
                banditIndex = i;
                break;
            }
        }
        repaint();
    }

    public JSONObject getLoadouts() {
        return loadouts;
    }

    public String getSelectedLoadout() {
        if (loadoutKeys.isEmpty()) return "Default";
        return loadoutKeys.get(loadoutIndex);
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
            public void focusGained(FocusEvent focusEvent) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent focusEvent) {
                repaint();
            }
        });
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                SwingUtilities.invokeLater(() -> repaint());
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                SwingUtilities.invokeLater(() -> {
                    if (field.getText().isBlank()) {
                        doPobClear(true);
                    } else {
                        repaint();
                    }
                });
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
            }
        });
        return field;
    }

    private void loadValues() {
        logPath = settings.getLogPath() != null ? settings.getLogPath() : "";
        guidePath = settings.getGuideFile() != null ? settings.getGuideFile() : "";
        fieldEncoded[0] = settings.getHotkeyNext();
        fieldEncoded[1] = settings.getHotkeyPrev();
        fieldEncoded[2] = settings.getHotkeyMove();
        fieldEncoded[3] = settings.getHotkeyTimer();
        fieldEncoded[4] = settings.getHotkeySettings();
        fieldEncoded[5] = settings.getHotkeyPause();
        String savedBandit = settings.getBandit();
        banditIndex = 0;
        for (int i = 0; i < BANDIT_OPTIONS.length; i++) {
            if (BANDIT_OPTIONS[i].equals(savedBandit)) {
                banditIndex = i;
                break;
            }
        }
    }

    private void initFonts() {
        titleFont = new Font(Config.FONT_NAME, Font.BOLD, 19);
        labelFont = new Font(Config.FONT_NAME, Font.PLAIN, 15);
        valueFont = new Font(Config.FONT_NAME, Font.BOLD, 15);
        hintFont = new Font(Config.FONT_NAME, Font.PLAIN, 12);
        closeBoldFont = new Font(Config.FONT_NAME, Font.BOLD, 12);
    }

    private void positionPobTextField() {
        int pobRowY = PAD + 28 + GAP + ROW_H + GAP + ROW_H + GAP;
        int clearWidth = 60;
        int loadWidth = 80;
        int pobFieldWidth = W - loadWidth - GAP - clearWidth - GAP;
        FontMetrics labelMetrics = getFontMetrics(labelFont);
        if (labelMetrics == null) return;
        int labelWidth = labelMetrics.stringWidth("POB") + 20;
        int fieldHeight = pobTextField.getPreferredSize().height;
        int textX = PAD + labelWidth;
        int textWidth = pobFieldWidth - labelWidth - 12;
        int textY = pobRowY + (ROW_H - fieldHeight) / 2;
        pobTextField.setBounds(textX, textY, textWidth, fieldHeight);
    }

    private void initMouseListener() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent mouseEvent) {
                Point point = mouseEvent.getPoint();

                if (closeRect.contains(point)) {
                    hotkeyHandler.stopListening();
                    onClose.run();
                    return;
                }
                if (pobLoadRect.contains(point)) {
                    hotkeyHandler.stopListening();
                    doLoad();
                    return;
                }
                if (pobClearRect.contains(point)) {
                    hotkeyHandler.stopListening();
                    doPobClear();
                    return;
                }
                if (guideClearRect.contains(point)) {
                    hotkeyHandler.stopListening();
                    doGuideClear();
                    return;
                }
                if (guideEditRect.contains(point)) {
                    hotkeyHandler.stopListening();
                    doGuideEdit();
                    return;
                }
                if (guideBrowseRect.contains(point)) {
                    hotkeyHandler.stopListening();
                    browseForGuideFile();
                    return;
                }
                if (logBrowseRect.contains(point)) {
                    hotkeyHandler.stopListening();
                    browseForLogFile();
                    return;
                }
                if (forceShowRect.contains(point)) {
                    hotkeyHandler.stopListening();
                    overlayForceVisible = !overlayForceVisible;
                    if (overlayForceVisible) onForceShowOverlay.run();
                    else onForceHideOverlay.run();
                    repaint();
                    return;
                }
                if (recalibrateRect.contains(point)) {
                    hotkeyHandler.stopListening();
                    onRecalibrate.run();
                    showRecalibrateConfirmation = true;
                    repaint();
                    Timer resetTimer = new Timer(SAVE_FEEDBACK_DURATION_MS, timerEvent -> {
                        showRecalibrateConfirmation = false;
                        repaint();
                    });
                    resetTimer.setRepeats(false);
                    resetTimer.start();
                    return;
                }
                if (mouseEvent.getButton() == MouseEvent.BUTTON1
                        && pobTextField.getBounds().contains(point)) {
                    hotkeyHandler.stopListening();
                    pobTextField.requestFocusInWindow();
                    return;
                }
                if (hotkeyHandler.isListening()) return;
                if (mouseEvent.getButton() == MouseEvent.BUTTON1) {
                    if (banditRect.contains(point)) {
                        banditIndex = (banditIndex + 1) % BANDIT_OPTIONS.length;
                        doSave();
                        repaint();
                        return;
                    }
                    if (loadoutRect.contains(point) && loadoutKeys.size() > 1) {
                        loadoutIndex = (loadoutIndex + 1) % loadoutKeys.size();
                        doSave();
                        repaint();
                        return;
                    }
                    for (int i = 0; i < fieldRects.length; i++) {
                        if (fieldRects[i] != null && fieldRects[i].contains(point)) {
                            hotkeyHandler.startListening(i);
                            return;
                        }
                    }
                }
                hotkeyHandler.stopListening();
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
                chooser.setCurrentDirectory(current.isDirectory() ? current : current.getParentFile());
                chooser.setSelectedFile(current);
            }
            int result = chooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                logPath = chooser.getSelectedFile().getAbsolutePath();
                doSave();
                repaint();
            }
        } finally {
            if (parentWindow != null) {
                parentWindow.setAlwaysOnTop(true);
                parentWindow.toFront();
            }
        }
    }

    private void browseForGuideFile() {
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        try {
            if (parentWindow != null) parentWindow.setAlwaysOnTop(false);
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select road.map");
            File current = guidePath.isBlank() ? new File(".") : new File(guidePath);
            if (current.exists()) {
                chooser.setCurrentDirectory(current.isDirectory() ? current : current.getParentFile());
                chooser.setSelectedFile(current);
            }
            int result = chooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                guidePath = chooser.getSelectedFile().getAbsolutePath();
                doSave();
                onGuideFileChanged.accept(guidePath);
                repaint();
            }
        } finally {
            if (parentWindow != null) {
                parentWindow.setAlwaysOnTop(true);
                parentWindow.toFront();
            }
        }
    }

    private void doGuideClear() {
        if (guidePath.isBlank()) return;
        guidePath = "";
        doSave();
        onGuideFileChanged.accept("");
        repaint();
    }

    private void doGuideEdit() {
        if (guidePath.isBlank()) return;
        try {
            Runtime.getRuntime().exec(new String[]{"notepad.exe", guidePath});
        } catch (Exception exception) {
            Logger.error("[SettingsPanel] Failed to open guide in notepad: {}", exception.getMessage());
        }
    }

    private void doPobClear() {
        doPobClear(false);
    }

    private void doPobClear(boolean fieldAlreadyEmpty) {
        if (!fieldAlreadyEmpty) pobTextField.setText("");
        settings.setPobInput("");
        settings.save();
        onSave.run();
        onPobClear.run();
        repaint();
    }

    private void doSave() {
        settings.setLogPath(logPath);
        settings.setBandit(BANDIT_OPTIONS[banditIndex]);
        settings.setLoadout(getSelectedLoadout());
        settings.setPobInput(pobTextField.getText());
        settings.setGuideFile(guidePath);
        settings.setHotkeyNext(fieldEncoded[0]);
        settings.setHotkeyPrev(fieldEncoded[1]);
        settings.setHotkeyMove(fieldEncoded[2]);
        settings.setHotkeyTimer(fieldEncoded[3]);
        settings.setHotkeySettings(fieldEncoded[4]);
        settings.setHotkeyPause(fieldEncoded[5]);
        settings.save();
        onSave.run();
    }

    private void doLoad() {
        String pobText = pobTextField.getText().trim();
        if (pobText.isBlank()) {
            doPobClear();
            return;
        }
        settings.setPobInput(pobText);
        settings.save();
        onSave.run();
        onPobLoad.accept(pobText);
        showLoadConfirmation = true;
        repaint();
        Timer resetTimer = new Timer(SAVE_FEEDBACK_DURATION_MS, timerEvent -> {
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
                + ROW_H + GAP
                + ROW_H + GAP
                + ROW_H + GAP
                + 18 + GAP
                + (ROW_H + GAP) * gridRowCount + GAP
                + ROW_H + PAD;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
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

        int closeX = x + w - 22;
        closeRect = new Rectangle(closeX, y, 22, 22);
        g.setColor(COL_CLOSE);
        g.fillRoundRect(closeX, y, 22, 22, 6, 6);
        g.setFont(closeBoldFont);
        g.setColor(Color.WHITE);
        FontMetrics fontMetrics = g.getFontMetrics();
        g.drawString(
                "X",
                closeX + (22 - fontMetrics.stringWidth("X")) / 2,
                y + 15
        );

        y += 28 + GAP;

        int buttonWidth = 80;
        int fieldWidth = w - buttonWidth - GAP;
        paintField(g, labelFont, valueFont, x, y, fieldWidth, "Log File", logPath, false, false);
        logRect = new Rectangle(x, y, fieldWidth, ROW_H);
        paintButton(g, valueFont, x + fieldWidth + GAP, y, buttonWidth, "Browse", BG_SAVE, COL_BORDER, COL_GOLD);
        logBrowseRect = new Rectangle(x + fieldWidth + GAP, y, buttonWidth, ROW_H);

        y += ROW_H + GAP;

        int guideClearWidth = 60;
        int guideEditWidth = 60;
        int guideBrowseWidth = 80;
        int guideButtonsWidth = guideClearWidth + GAP + guideEditWidth + GAP + guideBrowseWidth;
        int guideFieldWidth = w - guideButtonsWidth - GAP;
        String guideDisplay = guidePath.isBlank() ? "default (road.map)" : guidePath;
        paintField(g, labelFont, valueFont, x, y, guideFieldWidth, "Guide", guideDisplay, false, false);
        int guideButtonX = x + guideFieldWidth + GAP;
        boolean guideIsCustom = !guidePath.isBlank();
        paintButton(
                g, valueFont,
                guideButtonX, y, guideClearWidth,
                "Clear",
                guideIsCustom ? BG_DANGER : BG_FIELD_DISABLED,
                guideIsCustom ? COL_DANGER_BORDER : COL_BORDER_DISABLED,
                guideIsCustom ? COL_DANGER_TEXT : COL_DISABLED
        );
        guideClearRect = guideIsCustom ? new Rectangle(guideButtonX, y, guideClearWidth, ROW_H) : new Rectangle();
        guideButtonX += guideClearWidth + GAP;
        paintButton(
                g, valueFont,
                guideButtonX, y, guideEditWidth,
                "Edit",
                guideIsCustom ? BG_SAVE : BG_FIELD_DISABLED,
                guideIsCustom ? COL_BORDER : COL_BORDER_DISABLED,
                guideIsCustom ? COL_GOLD : COL_DISABLED
        );
        guideEditRect = guideIsCustom ? new Rectangle(guideButtonX, y, guideEditWidth, ROW_H) : new Rectangle();
        guideButtonX += guideEditWidth + GAP;
        paintButton(g, valueFont, guideButtonX, y, guideBrowseWidth, "Browse", BG_SAVE, COL_BORDER, COL_GOLD);
        guideBrowseRect = new Rectangle(guideButtonX, y, guideBrowseWidth, ROW_H);

        y += ROW_H + GAP;

        int clearWidth = 60;
        int loadWidth = 80;
        int pobFieldWidth = w - loadWidth - GAP - clearWidth - GAP;
        boolean pobFocused = pobTextField.hasFocus();
        paintFieldBackground(g, labelFont, x, y, pobFieldWidth, "POB", pobFocused);
        int pobButtonX = x + pobFieldWidth + GAP;
        boolean pobHasContent = !pobTextField.getText().isBlank();
        paintButton(
                g, valueFont,
                pobButtonX, y, clearWidth,
                "Clear",
                pobHasContent ? BG_DANGER : BG_FIELD_DISABLED,
                pobHasContent ? COL_DANGER_BORDER : COL_BORDER_DISABLED,
                pobHasContent ? COL_DANGER_TEXT : COL_DISABLED
        );
        pobClearRect = pobHasContent ? new Rectangle(pobButtonX, y, clearWidth, ROW_H) : new Rectangle();
        pobButtonX += clearWidth + GAP;
        paintButton(
                g, valueFont,
                pobButtonX, y, loadWidth,
                showLoadConfirmation ? "Loaded!" : "Load",
                showLoadConfirmation ? BG_LOAD_OK : BG_SAVE,
                showLoadConfirmation ? COL_LOAD_OK_BORDER : COL_BORDER,
                showLoadConfirmation ? COL_LOAD_OK_TEXT : COL_GOLD
        );
        pobLoadRect = new Rectangle(pobButtonX, y, loadWidth, ROW_H);

        y += ROW_H + GAP;

        boolean hasLoadouts = loadoutKeys.size() > 1;
        String loadoutValue = loadoutKeys.isEmpty() ? "Default" : loadoutKeys.get(loadoutIndex);
        paintFieldCycleable(
                g, labelFont, valueFont, hintFont, x, y, w,
                "Skill Loadout", loadoutValue,
                hasLoadouts, loadoutIndex, Math.max(1, loadoutKeys.size())
        );
        loadoutRect = new Rectangle(x, y, w, ROW_H);

        y += ROW_H + GAP;

        paintFieldCycleable(
                g, labelFont, valueFont, hintFont, x, y, w,
                "Bandit Quest", BANDIT_OPTIONS[banditIndex],
                true, banditIndex, BANDIT_OPTIONS.length
        );
        banditRect = new Rectangle(x, y, w, ROW_H);

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
            boolean listening = hotkeyHandler.listeningIndex() == i;
            String display = listening ? "..."
                    : fieldEncoded[i] == 0 ? "-"
                    : HotkeyLabel.labelOf(fieldEncoded[i]);
            paintField(g, labelFont, valueFont, cellX, cellY, cellWidth, ROW_LABELS[i], display, listening, true);
            fieldRects[i] = new Rectangle(cellX, cellY, cellWidth, ROW_H);
        }

        int gridRowCount = (int) Math.ceil((double) ROW_LABELS.length / GRID_COLS);
        y += gridRowCount * (ROW_H + GAP);

        int halfWidth = (w - GAP) / 2;

        forceShowRect = new Rectangle(x, y, halfWidth, ROW_H);
        paintButton(
                g, valueFont,
                x, y, halfWidth,
                overlayForceVisible ? "Overlay: Shown" : "Overlay: Hidden",
                overlayForceVisible ? BG_TOGGLE_ON : BG_TOGGLE_OFF,
                overlayForceVisible ? COL_TOGGLE_ON_BORDER : COL_TOGGLE_OFF_BORDER,
                overlayForceVisible ? COL_TOGGLE_ON_TEXT : COL_TOGGLE_OFF_TEXT
        );

        int recalX = x + halfWidth + GAP;
        recalibrateRect = new Rectangle(recalX, y, halfWidth, ROW_H);
        paintButton(
                g, valueFont,
                recalX, y, halfWidth,
                showRecalibrateConfirmation ? "Done!" : "Recalibrate",
                showRecalibrateConfirmation ? BG_RECAL_OK : BG_SAVE,
                showRecalibrateConfirmation ? COL_RECAL_OK_BORDER : COL_BORDER,
                showRecalibrateConfirmation ? COL_RECAL_OK_TEXT : COL_GOLD
        );

        g.dispose();
    }
}