package com.notepad.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

// Provider 的增删改查、软删除与待办标记
@RunWith(AndroidJUnit4.class)
public class NotePadProviderTest {

    private static final String PREFIX = "autotest-";

    private ContentResolver resolver;
    private final List<Uri> created = new ArrayList<>();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        resolver = context.getContentResolver();
    }

    @After
    public void tearDown() {
        for (Uri uri : created) {
            resolver.delete(uri, null, null);
        }
        created.clear();
    }

    private Uri insertNote(String title, String text, int todo, int done, long deletedAt) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, PREFIX + title);
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);
        long now = System.currentTimeMillis();
        values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
        values.put(NotePad.Notes.COLUMN_NAME_IS_TODO, todo);
        values.put(NotePad.Notes.COLUMN_NAME_IS_DONE, done);
        values.put(NotePad.Notes.COLUMN_NAME_DELETED_AT, deletedAt);

        Uri uri = resolver.insert(NotePad.Notes.CONTENT_URI, values);
        assertNotNull(uri);
        created.add(uri);
        return uri;
    }

    private Cursor queryOne(Uri uri) {
        Cursor cursor = resolver.query(uri, null, null, null, null);
        assertNotNull(cursor);
        assertTrue(cursor.moveToFirst());
        return cursor;
    }

    private int countByTitle(String extraSelection, String[] extraArgs) {
        String selection = NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ?";
        if (extraSelection != null) {
            selection += " AND " + extraSelection;
        }
        List<String> args = new ArrayList<>();
        args.add(PREFIX + "%");
        if (extraArgs != null) {
            for (String arg : extraArgs) {
                args.add(arg);
            }
        }
        Cursor cursor = resolver.query(NotePad.Notes.CONTENT_URI, null,
                selection, args.toArray(new String[0]), null);
        assertNotNull(cursor);
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    @Test
    public void insertAndQueryNote() {
        Uri uri = insertNote("insert", "hello provider", 0, 0, 0);
        Cursor cursor = queryOne(uri);
        try {
            assertEquals(PREFIX + "insert", cursor.getString(
                    cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_TITLE)));
            assertEquals("hello provider", cursor.getString(
                    cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_NOTE)));
        } finally {
            cursor.close();
        }
    }

    @Test
    public void updateNoteTitle() {
        Uri uri = insertNote("before", "body", 0, 0, 0);

        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, PREFIX + "after");
        assertEquals(1, resolver.update(uri, values, null, null));

        Cursor cursor = queryOne(uri);
        try {
            assertEquals(PREFIX + "after", cursor.getString(
                    cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_TITLE)));
        } finally {
            cursor.close();
        }
    }

    @Test
    public void deletedNotesAreFilteredFromNormalList() {
        insertNote("keep", "a", 0, 0, 0);
        insertNote("removed", "b", 0, 0, System.currentTimeMillis());

        assertEquals(1, countByTitle("deleted_at = 0", null));
        assertEquals(1, countByTitle("deleted_at > 0", null));
    }

    @Test
    public void todoDoneFlagCanBeToggled() {
        Uri uri = insertNote("todo", "buy milk", 1, 0, 0);

        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_IS_DONE, 1);
        assertEquals(1, resolver.update(uri, values, null, null));

        Cursor cursor = queryOne(uri);
        try {
            assertEquals(1, cursor.getInt(
                    cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_IS_TODO)));
            assertEquals(1, cursor.getInt(
                    cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_IS_DONE)));
        } finally {
            cursor.close();
        }
    }

    @Test
    public void deleteRemovesNote() {
        Uri uri = insertNote("delete", "x", 0, 0, 0);

        assertEquals(1, resolver.delete(uri, null, null));
        created.remove(uri);

        Cursor cursor = resolver.query(uri, null, null, null, null);
        assertNotNull(cursor);
        try {
            assertEquals(0, cursor.getCount());
        } finally {
            cursor.close();
        }
    }

    @Test
    public void duplicateCategoryIsRejected() {
        ContentValues values = new ContentValues();
        values.put(NotePad.Categories.COLUMN_NAME_NAME, PREFIX + "category");

        Uri first = resolver.insert(NotePad.Categories.CONTENT_URI, values);
        assertNotNull(first);
        try {
            Uri second = resolver.insert(NotePad.Categories.CONTENT_URI, values);
            assertNull(second);
        } finally {
            resolver.delete(first, null, null);
        }
    }
}
