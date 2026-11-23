package com.notepad.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

//备份
//笔记与分类的 JSON 导出和恢复
public final class BackupManager {

    private BackupManager() {
    }

    //导出全部笔记与分类到目标文件
    public static int exportNotes(Context context, Uri target) throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "NotePad");
        root.put("version", 1);
        root.put("exportedAt", System.currentTimeMillis());

        JSONArray categories = new JSONArray();
        Cursor categoryCursor = context.getContentResolver().query(
                NotePad.Categories.CONTENT_URI,
                new String[] { NotePad.Categories.COLUMN_NAME_NAME },
                null, null,
                NotePad.Categories.DEFAULT_SORT_ORDER);
        if (categoryCursor != null) {
            try {
                while (categoryCursor.moveToNext()) {
                    categories.put(categoryCursor.getString(0));
                }
            } finally {
                categoryCursor.close();
            }
        }
        root.put("categories", categories);

        JSONArray notes = new JSONArray();
        Cursor cursor = context.getContentResolver().query(
                NotePad.Notes.CONTENT_URI,
                new String[] {
                        NotePad.Notes.COLUMN_NAME_TITLE,
                        NotePad.Notes.COLUMN_NAME_NOTE,
                        NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                        NotePad.Notes.COLUMN_NAME_CATEGORY,
                        NotePad.Notes.COLUMN_NAME_IS_FAVORITE,
                        NotePad.Notes.COLUMN_NAME_IS_TODO,
                        NotePad.Notes.COLUMN_NAME_IS_DONE,
                        NotePad.Notes.COLUMN_NAME_PINNED
                },
                NotePad.Notes.COLUMN_NAME_DELETED_AT + " = 0",
                null,
                NotePad.Notes.DEFAULT_SORT_ORDER);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    JSONObject note = new JSONObject();
                    note.put("title", cursor.getString(0));
                    note.put("note", cursor.getString(1));
                    note.put("created", cursor.getLong(2));
                    note.put("modified", cursor.getLong(3));
                    note.put("category", cursor.getString(4));
                    note.put("favorite", cursor.getInt(5));
                    note.put("todo", cursor.getInt(6));
                    note.put("done", cursor.getInt(7));
                    note.put("pinned", cursor.getInt(8));
                    notes.put(note);
                }
            } finally {
                cursor.close();
            }
        }
        root.put("notes", notes);

        try (OutputStream out = context.getContentResolver().openOutputStream(target, "wt")) {
            if (out == null) {
                throw new IllegalStateException("Cannot open output stream");
            }
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }

        return notes.length();
    }

    //从文件恢复，按标题与创建时间去重
    public static int[] importNotes(Context context, Uri source) throws Exception {
        String json;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) {
                throw new IllegalStateException("Cannot open input stream");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            json = buffer.toString("UTF-8");
        }

        JSONObject root = new JSONObject(json);
        if (!root.has("notes")) {
            throw new IllegalArgumentException("Not a NotePad backup");
        }

        JSONArray categories = root.optJSONArray("categories");
        if (categories != null) {
            for (int i = 0; i < categories.length(); i++) {
                String name = categories.optString(i, "").trim();
                if (name.isEmpty() || categoryExists(context, name)) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put(NotePad.Categories.COLUMN_NAME_NAME, name);
                try {
                    context.getContentResolver().insert(NotePad.Categories.CONTENT_URI, values);
                } catch (Exception ignored) {

                }
            }
        }

        Set<String> existing = new HashSet<>();
        Cursor cursor = context.getContentResolver().query(
                NotePad.Notes.CONTENT_URI,
                new String[] {
                        NotePad.Notes.COLUMN_NAME_TITLE,
                        NotePad.Notes.COLUMN_NAME_CREATE_DATE
                },
                null, null, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    existing.add(cursor.getString(0) + "#" + cursor.getLong(1));
                }
            } finally {
                cursor.close();
            }
        }

        int imported = 0;
        int skipped = 0;
        JSONArray notes = root.getJSONArray("notes");
        for (int i = 0; i < notes.length(); i++) {
            JSONObject note = notes.optJSONObject(i);
            if (note == null) {
                skipped++;
                continue;
            }

            String title = note.optString("title", "");
            long created = note.optLong("created", System.currentTimeMillis());
            if (existing.contains(title + "#" + created)) {
                skipped++;
                continue;
            }

            ContentValues values = new ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
            values.put(NotePad.Notes.COLUMN_NAME_NOTE, note.optString("note", ""));
            values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, created);
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                    note.optLong("modified", created));
            values.put(NotePad.Notes.COLUMN_NAME_CATEGORY,
                    note.optString("category", NotePad.Notes.CATEGORY_DEFAULT));
            values.put(NotePad.Notes.COLUMN_NAME_IS_FAVORITE, note.optInt("favorite", 0));
            values.put(NotePad.Notes.COLUMN_NAME_IS_TODO, note.optInt("todo", 0));
            values.put(NotePad.Notes.COLUMN_NAME_IS_DONE, note.optInt("done", 0));
            values.put(NotePad.Notes.COLUMN_NAME_PINNED, note.optInt("pinned", 0));
            values.put(NotePad.Notes.COLUMN_NAME_DELETED_AT, 0);

            Uri inserted = context.getContentResolver()
                    .insert(NotePad.Notes.CONTENT_URI, values);
            if (inserted != null) {
                imported++;
            } else {
                skipped++;
            }
        }

        return new int[] { imported, skipped };
    }

    // 判断分类是否已存在
    private static boolean categoryExists(Context context, String name) {
        Cursor cursor = context.getContentResolver().query(
                NotePad.Categories.CONTENT_URI,
                new String[] { NotePad.Categories.COLUMN_NAME_NAME },
                NotePad.Categories.COLUMN_NAME_NAME + " = ?",
                new String[] { name },
                null);
        try {
            return cursor != null && cursor.getCount() > 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
