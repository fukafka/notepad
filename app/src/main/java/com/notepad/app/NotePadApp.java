package com.notepad.app;

import android.app.Application;

//启动时应用主题偏好
public class NotePadApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        ThemePrefs.apply(this);
    }
}
