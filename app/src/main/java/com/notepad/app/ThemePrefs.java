package com.notepad.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

// 主题偏好：深浅色模式与主题色
public final class ThemePrefs {

    private static final String PREFS_NAME = "notepad_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_THEME_COLOR = "theme_color";

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    public static final int COLOR_DEFAULT = 0;
    public static final int COLOR_INDIGO = 1;
    public static final int COLOR_TEAL = 2;
    public static final int COLOR_ORANGE = 3;
    public static final int COLOR_ROSE = 4;

    private ThemePrefs() {
    }

    // 读取当前主题模式
    public static int getMode(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_THEME_MODE, MODE_SYSTEM);
    }

    // 保存主题模式
    public static void setMode(Context context, int mode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_THEME_MODE, mode)
                .apply();
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode));
    }

    // 把主题模式应用到当前进程
    public static void apply(Context context) {
        AppCompatDelegate.setDefaultNightMode(toNightMode(getMode(context)));
    }

    // 读取当前主题色
    public static int getColor(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_THEME_COLOR, COLOR_DEFAULT);
    }

    // 保存主题色
    public static void setColor(Context context, int color) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_THEME_COLOR, color)
                .apply();
    }

    // 按主题色返回对应的主题资源
    public static int themeRes(Context context) {
        switch (getColor(context)) {
            case COLOR_INDIGO:
                return R.style.Theme_NotePad_Indigo;
            case COLOR_TEAL:
                return R.style.Theme_NotePad_Teal;
            case COLOR_ORANGE:
                return R.style.Theme_NotePad_Orange;
            case COLOR_ROSE:
                return R.style.Theme_NotePad_Rose;
            default:
                return R.style.Theme_NotePad;
        }
    }

    private static int toNightMode(int mode) {
        switch (mode) {
            case MODE_LIGHT:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case MODE_DARK:
                return AppCompatDelegate.MODE_NIGHT_YES;
            default:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }
}
