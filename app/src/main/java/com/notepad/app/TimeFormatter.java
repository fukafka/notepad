package com.notepad.app;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

// 把时间戳转成今天、昨天或日期形式
public final class TimeFormatter {

    private TimeFormatter() {
    }

    // 格式化时间显示
    public static String format(Context context, long millis) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(millis);

        if (isSameDay(now, target)) {
            return context.getString(R.string.time_today, timeOfDay(target));
        }

        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(yesterday, target)) {
            return context.getString(R.string.time_yesterday, timeOfDay(target));
        }

        String pattern = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                ? "M月d日"
                : "yyyy年M月d日";
        return new SimpleDateFormat(pattern, Locale.CHINA).format(new Date(millis));
    }

    private static boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static String timeOfDay(Calendar c) {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(c.getTime());
    }
}
