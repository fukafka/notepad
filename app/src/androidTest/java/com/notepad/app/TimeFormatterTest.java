package com.notepad.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;

// 时间显示的今天、昨天与日期分支
@RunWith(AndroidJUnit4.class)
public class TimeFormatterTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void todayIsShownAsToday() {
        String text = TimeFormatter.format(context, System.currentTimeMillis());
        assertTrue(text.startsWith(context.getString(R.string.time_today, "").trim()));
    }

    @Test
    public void yesterdayIsShownAsYesterday() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -1);

        String text = TimeFormatter.format(context, calendar.getTimeInMillis());
        assertTrue(text.startsWith(context.getString(R.string.time_yesterday, "").trim()));
    }

    @Test
    public void olderDateUsesMonthAndDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -10);

        String text = TimeFormatter.format(context, calendar.getTimeInMillis());
        assertFalse(text.contains("今天"));
        assertFalse(text.contains("昨天"));
        assertTrue(text.contains("月"));
        assertTrue(text.contains("日"));
    }

    @Test
    public void previousYearIncludesYear() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -1);

        String text = TimeFormatter.format(context, calendar.getTimeInMillis());
        assertTrue(text.contains("年"));
    }
}
