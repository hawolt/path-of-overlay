package com.hawolt;

public final class Config {

    public static final String GAME_WINDOW_TITLE = "Path of Exile";

    public static final int DEFAULT_X_AT_1440P = 15;
    public static final int DEFAULT_Y_AT_1440P = 250;
    public static final int REFERENCE_WIDTH = 2560;
    public static final int REFERENCE_HEIGHT = 1440;

    public static final int VISIBLE_STEPS = 3;
    public static final int ZONE_SEEK_LOOKAHEAD = VISIBLE_STEPS + 2;

    public static final int VK_NEXT = 0x71;
    public static final int VK_PREV = 0x72;
    public static final int VK_MOVE = 0x73;
    public static final int VK_TIMER = 0x76;
    public static final int VK_SETTINGS = 0x75;
    public static final int VK_PAUSE = 0x77;
    public static final int VK_RELOAD_GUIDE = 0x78;

    public static final int MOD_ALT = 0x0001;
    public static final int MOD_CONTROL = 0x0002;
    public static final int MOD_SHIFT = 0x0004;
    public static final int MOD_NOREPEAT = 0x4000;

    public static final int FONT_SIZE_STEP_0 = 22;
    public static final int FONT_SIZE_STEP_2 = 13;
    public static final int TIMER_ACT_FONT_SIZE = 18;
    public static final int TIMER_CLOCK_FONT_SIZE = 22;

    public static final int BG_ALPHA_STEP_0 = 40;
    public static final int BG_ALPHA_STEP_1 = 22;
    public static final int BG_ALPHA_STEP_2 = 10;

    public static final int TEXT_ALPHA_STEP_0 = 230;
    public static final int TEXT_ALPHA_STEP_1 = 160;
    public static final int TEXT_ALPHA_STEP_2 = 90;

    public static final String FONT_NAME = "Fontin";

    private Config() {
    }
}