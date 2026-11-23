package com.notepad.app;

import com.notepad.app.NotePad;

import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.ContentProvider.PipeDataWriter;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.LiveFolders;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

//把 content:// URI 映射为 SQLite 的增删改查
public class NotePadProvider extends ContentProvider implements PipeDataWriter<Cursor> {

    private static final String TAG = "NotePadProvider";

    private static final String DATABASE_NAME = "note_pad.db";

    private static final int DATABASE_VERSION = 6;

    private static HashMap<String, String> sNotesProjectionMap;

    private static HashMap<String, String> sLiveFolderProjectionMap;

    private static HashMap<String, String> sCategoriesProjectionMap;

    private static final String[] READ_NOTE_PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_TITLE,
    };
    private static final int READ_NOTE_NOTE_INDEX = 1;
    private static final int READ_NOTE_TITLE_INDEX = 2;

    private static final int NOTES = 1;

    private static final int NOTE_ID = 2;

    private static final int LIVE_FOLDER_NOTES = 3;

    private static final int CATEGORIES = 4;

    private static final int CATEGORY_ID = 5;

    private static final UriMatcher sUriMatcher;

    private DatabaseHelper mOpenHelper;

    static {

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        sUriMatcher.addURI(NotePad.AUTHORITY, "notes", NOTES);

        sUriMatcher.addURI(NotePad.AUTHORITY, "notes/#", NOTE_ID);

        sUriMatcher.addURI(NotePad.AUTHORITY, "live_folders/notes", LIVE_FOLDER_NOTES);

        sUriMatcher.addURI(NotePad.AUTHORITY, "categories", CATEGORIES);
        sUriMatcher.addURI(NotePad.AUTHORITY, "categories/#", CATEGORY_ID);

        sNotesProjectionMap = new HashMap<String, String>();

        sNotesProjectionMap.put(NotePad.Notes._ID, NotePad.Notes._ID);

        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_TITLE);

        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_NOTE, NotePad.Notes.COLUMN_NAME_NOTE);

        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                NotePad.Notes.COLUMN_NAME_CREATE_DATE);

        sNotesProjectionMap.put(
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE);

        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_CATEGORY,
                NotePad.Notes.COLUMN_NAME_CATEGORY);

        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_IS_FAVORITE,
                NotePad.Notes.COLUMN_NAME_IS_FAVORITE);

        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_DELETED_AT,
                NotePad.Notes.COLUMN_NAME_DELETED_AT);

        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_IS_TODO,
                NotePad.Notes.COLUMN_NAME_IS_TODO);

        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_IS_DONE,
                NotePad.Notes.COLUMN_NAME_IS_DONE);

        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_PINNED,
                NotePad.Notes.COLUMN_NAME_PINNED);

        sLiveFolderProjectionMap = new HashMap<String, String>();

        sLiveFolderProjectionMap.put(LiveFolders._ID, NotePad.Notes._ID + " AS " + LiveFolders._ID);

        sLiveFolderProjectionMap.put(LiveFolders.NAME, NotePad.Notes.COLUMN_NAME_TITLE + " AS " +
            LiveFolders.NAME);

        sCategoriesProjectionMap = new HashMap<String, String>();
        sCategoriesProjectionMap.put(NotePad.Categories._ID, NotePad.Categories._ID);
        sCategoriesProjectionMap.put(NotePad.Categories.COLUMN_NAME_NAME,
                NotePad.Categories.COLUMN_NAME_NAME);
        sCategoriesProjectionMap.put(NotePad.Categories.COLUMN_NAME_SORT_ORDER,
                NotePad.Categories.COLUMN_NAME_SORT_ORDER);
    }

   static class DatabaseHelper extends SQLiteOpenHelper {

       DatabaseHelper(Context context) {

           super(context, DATABASE_NAME, null, DATABASE_VERSION);
       }

       // 建库：首次创建时建笔记表
       @Override
       public void onCreate(SQLiteDatabase db) {
           db.execSQL("CREATE TABLE " + NotePad.Notes.TABLE_NAME + " ("
                   + NotePad.Notes._ID + " INTEGER PRIMARY KEY,"
                   + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT,"
                   + NotePad.Notes.COLUMN_NAME_NOTE + " TEXT,"
                   + NotePad.Notes.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                   + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " INTEGER,"
                   + NotePad.Notes.COLUMN_NAME_CATEGORY + " TEXT DEFAULT '"
                   + NotePad.Notes.CATEGORY_DEFAULT + "',"
                   + NotePad.Notes.COLUMN_NAME_IS_FAVORITE + " INTEGER NOT NULL DEFAULT 0,"
                   + NotePad.Notes.COLUMN_NAME_DELETED_AT + " INTEGER NOT NULL DEFAULT 0,"
                   + NotePad.Notes.COLUMN_NAME_IS_TODO + " INTEGER NOT NULL DEFAULT 0,"
                   + NotePad.Notes.COLUMN_NAME_IS_DONE + " INTEGER NOT NULL DEFAULT 0,"
                   + NotePad.Notes.COLUMN_NAME_PINNED + " INTEGER NOT NULL DEFAULT 0"
                   + ");");

           createCategoriesTable(db);
       }

       // 建分类表并写入默认分类
       private void createCategoriesTable(SQLiteDatabase db) {
           db.execSQL("CREATE TABLE IF NOT EXISTS " + NotePad.Categories.TABLE_NAME + " ("
                   + NotePad.Categories._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                   + NotePad.Categories.COLUMN_NAME_NAME + " TEXT NOT NULL UNIQUE,"
                   + NotePad.Categories.COLUMN_NAME_SORT_ORDER + " INTEGER NOT NULL DEFAULT 0"
                   + ");");

           String[] defaults = NotePad.Notes.CATEGORIES;
           for (int i = 0; i < defaults.length; i++) {
               ContentValues values = new ContentValues();
               values.put(NotePad.Categories.COLUMN_NAME_NAME, defaults[i]);
               values.put(NotePad.Categories.COLUMN_NAME_SORT_ORDER, i);
               db.insertWithOnConflict(NotePad.Categories.TABLE_NAME, null, values,
                       SQLiteDatabase.CONFLICT_IGNORE);
           }
       }

       // 升级：按版本号增量加列，保留旧数据
       @Override
       public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
           Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

           if (oldVersion < 3) {
               db.execSQL("ALTER TABLE " + NotePad.Notes.TABLE_NAME
                       + " ADD COLUMN " + NotePad.Notes.COLUMN_NAME_CATEGORY
                       + " TEXT DEFAULT '" + NotePad.Notes.CATEGORY_DEFAULT + "'");
               db.execSQL("ALTER TABLE " + NotePad.Notes.TABLE_NAME
                       + " ADD COLUMN " + NotePad.Notes.COLUMN_NAME_IS_FAVORITE
                       + " INTEGER NOT NULL DEFAULT 0");
               db.execSQL("ALTER TABLE " + NotePad.Notes.TABLE_NAME
                       + " ADD COLUMN " + NotePad.Notes.COLUMN_NAME_DELETED_AT
                       + " INTEGER NOT NULL DEFAULT 0");
           }

           if (oldVersion < 4) {
               createCategoriesTable(db);
           }

           if (oldVersion < 5) {
               db.execSQL("ALTER TABLE " + NotePad.Notes.TABLE_NAME
                       + " ADD COLUMN " + NotePad.Notes.COLUMN_NAME_IS_TODO
                       + " INTEGER NOT NULL DEFAULT 0");
               db.execSQL("ALTER TABLE " + NotePad.Notes.TABLE_NAME
                       + " ADD COLUMN " + NotePad.Notes.COLUMN_NAME_IS_DONE
                       + " INTEGER NOT NULL DEFAULT 0");
           }

           if (oldVersion < 6) {
               db.execSQL("ALTER TABLE " + NotePad.Notes.TABLE_NAME
                       + " ADD COLUMN " + NotePad.Notes.COLUMN_NAME_PINNED
                       + " INTEGER NOT NULL DEFAULT 0");
           }
       }
   }

   // Provider 初始化：打开数据库并注册 URI 路由
   @Override
   public boolean onCreate() {

       mOpenHelper = new DatabaseHelper(getContext());

       return true;
   }

   // 查询：按 URI 决定查笔记还是分类
   @Override
   public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
           String sortOrder) {

       SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

       switch (sUriMatcher.match(uri)) {

           case NOTES:
               qb.setTables(NotePad.Notes.TABLE_NAME);
               qb.setProjectionMap(sNotesProjectionMap);
               break;

           case NOTE_ID:
               qb.setTables(NotePad.Notes.TABLE_NAME);
               qb.setProjectionMap(sNotesProjectionMap);
               qb.appendWhere(
                   NotePad.Notes._ID +
                   "=" +

                   uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION));
               break;

           case LIVE_FOLDER_NOTES:
               qb.setTables(NotePad.Notes.TABLE_NAME);

               qb.setProjectionMap(sLiveFolderProjectionMap);
               break;

           case CATEGORIES:
               qb.setTables(NotePad.Categories.TABLE_NAME);
               qb.setProjectionMap(sCategoriesProjectionMap);
               break;

           case CATEGORY_ID:
               qb.setTables(NotePad.Categories.TABLE_NAME);
               qb.setProjectionMap(sCategoriesProjectionMap);
               qb.appendWhere(NotePad.Categories._ID + "="
                       + uri.getPathSegments().get(NotePad.Categories.CATEGORY_ID_PATH_POSITION));
               break;

           default:

               throw new IllegalArgumentException("Unknown URI " + uri);
       }

       String orderBy;

       if (TextUtils.isEmpty(sortOrder)) {
           orderBy = sUriMatcher.match(uri) == CATEGORIES || sUriMatcher.match(uri) == CATEGORY_ID
                   ? NotePad.Categories.DEFAULT_SORT_ORDER
                   : NotePad.Notes.DEFAULT_SORT_ORDER;
       } else {

           orderBy = sortOrder;
       }

       SQLiteDatabase db = mOpenHelper.getReadableDatabase();

       Cursor c = qb.query(
           db,
           projection,
           selection,
           selectionArgs,
           null,
           null,
           orderBy
       );

       c.setNotificationUri(getContext().getContentResolver(), uri);
       return c;
   }

   // 返回 URI 对应的 MIME 类型
   @Override
   public String getType(Uri uri) {

       switch (sUriMatcher.match(uri)) {

           case NOTES:
           case LIVE_FOLDER_NOTES:
               return NotePad.Notes.CONTENT_TYPE;

           case NOTE_ID:
               return NotePad.Notes.CONTENT_ITEM_TYPE;

           case CATEGORIES:
               return NotePad.Categories.CONTENT_TYPE;
           case CATEGORY_ID:
               return NotePad.Categories.CONTENT_ITEM_TYPE;

           default:
               throw new IllegalArgumentException("Unknown URI " + uri);
       }
    }

    static ClipDescription NOTE_STREAM_TYPES = new ClipDescription(null,
            new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN });

    @Override
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {

        switch (sUriMatcher.match(uri)) {

            case NOTES:
            case LIVE_FOLDER_NOTES:
                return null;

            case NOTE_ID:
                return NOTE_STREAM_TYPES.filterMimeTypes(mimeTypeFilter);

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
            }
    }

    @Override
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts)
            throws FileNotFoundException {

        String[] mimeTypes = getStreamTypes(uri, mimeTypeFilter);

        if (mimeTypes != null) {

            Cursor c = query(
                    uri,
                    READ_NOTE_PROJECTION,

                    null,
                    null,
                    null

            );

            if (c == null || !c.moveToFirst()) {

                if (c != null) {
                    c.close();
                }

                throw new FileNotFoundException("Unable to query " + uri);
            }

            return new AssetFileDescriptor(
                    openPipeHelper(uri, mimeTypes[0], opts, c, this), 0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        }

        return super.openTypedAssetFile(uri, mimeTypeFilter, opts);
    }

    @Override
    public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType,
            Bundle opts, Cursor c) {

        FileOutputStream fout = new FileOutputStream(output.getFileDescriptor());
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(fout, "UTF-8"));
            pw.println(c.getString(READ_NOTE_TITLE_INDEX));
            pw.println("");
            pw.println(c.getString(READ_NOTE_NOTE_INDEX));
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "Ooops", e);
        } finally {
            c.close();
            if (pw != null) {
                pw.flush();
            }
            try {
                fout.close();
            } catch (IOException e) {
            }
        }
    }

    // 插入笔记：补默认值后写库并通知刷新
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        int match = sUriMatcher.match(uri);
        if (match == CATEGORIES) {
            return insertCategory(initialValues);
        }

        if (match != NOTES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;

        if (initialValues != null) {
            values = new ContentValues(initialValues);

        } else {

            values = new ContentValues();
        }

        Long now = Long.valueOf(System.currentTimeMillis());

        if (values.containsKey(NotePad.Notes.COLUMN_NAME_CREATE_DATE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
        }

        if (values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
        }

        if (values.containsKey(NotePad.Notes.COLUMN_NAME_TITLE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_TITLE,
                    getContext().getString(R.string.untitled_note));
        }

        if (values.containsKey(NotePad.Notes.COLUMN_NAME_NOTE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
        }

        if (values.containsKey(NotePad.Notes.COLUMN_NAME_CATEGORY) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, NotePad.Notes.CATEGORY_DEFAULT);
        }
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_IS_FAVORITE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_IS_FAVORITE, 0);
        }
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_DELETED_AT) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_DELETED_AT, 0);
        }
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_IS_TODO) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_IS_TODO, 0);
        }
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_IS_DONE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_IS_DONE, 0);
        }
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_PINNED) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_PINNED, 0);
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        long rowId = db.insert(
            NotePad.Notes.TABLE_NAME,
            NotePad.Notes.COLUMN_NAME_NOTE,

            values

        );

        if (rowId > 0) {

            Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, rowId);

            getContext().getContentResolver().notifyChange(noteUri, null);
            return noteUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    // 插入分类：重名时返回 null
    private Uri insertCategory(ContentValues initialValues) {
        ContentValues values = initialValues == null
                ? new ContentValues()
                : new ContentValues(initialValues);

        String name = values.getAsString(NotePad.Categories.COLUMN_NAME_NAME);
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }
        name = name.trim();
        values.put(NotePad.Categories.COLUMN_NAME_NAME, name);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        if (!values.containsKey(NotePad.Categories.COLUMN_NAME_SORT_ORDER)) {
            int nextOrder = 0;
            Cursor cursor = db.rawQuery(
                    "SELECT COALESCE(MAX(" + NotePad.Categories.COLUMN_NAME_SORT_ORDER
                            + "), 0) + 1 FROM " + NotePad.Categories.TABLE_NAME, null);
            try {
                if (cursor.moveToFirst()) {
                    nextOrder = cursor.getInt(0);
                }
            } finally {
                cursor.close();
            }
            values.put(NotePad.Categories.COLUMN_NAME_SORT_ORDER, nextOrder);
        }

        long rowId = db.insert(NotePad.Categories.TABLE_NAME, null, values);
        if (rowId > 0) {
            Uri categoryUri =
                    ContentUris.withAppendedId(NotePad.Categories.CONTENT_ID_URI_BASE, rowId);
            getContext().getContentResolver().notifyChange(NotePad.Categories.CONTENT_URI, null);
            return categoryUri;
        }

        // 名称重复时约束冲突，返回 null 由调用方提示
        return null;
    }

    // 删除：笔记为软删除，分类为物理删除
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String finalWhere;

        int count;

        switch (sUriMatcher.match(uri)) {

            case NOTES:
                count = db.delete(
                    NotePad.Notes.TABLE_NAME,
                    where,
                    whereArgs
                );
                break;

            case NOTE_ID:

                finalWhere =
                        NotePad.Notes._ID +
                        " = " +
                        uri.getPathSegments().
                            get(NotePad.Notes.NOTE_ID_PATH_POSITION)
                ;

                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                count = db.delete(
                    NotePad.Notes.TABLE_NAME,
                    finalWhere,
                    whereArgs
                );
                break;

            case CATEGORIES:
                count = db.delete(NotePad.Categories.TABLE_NAME, where, whereArgs);
                break;

            case CATEGORY_ID:
                finalWhere = NotePad.Categories._ID + " = "
                        + uri.getPathSegments().get(NotePad.Categories.CATEGORY_ID_PATH_POSITION);
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                count = db.delete(NotePad.Categories.TABLE_NAME, finalWhere, whereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    // 更新：写库后通知观察者刷新
    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        String finalWhere;

        switch (sUriMatcher.match(uri)) {

            case NOTES:

                count = db.update(
                    NotePad.Notes.TABLE_NAME,
                    values,
                    where,
                    whereArgs
                );
                break;

            case NOTE_ID:

                String noteId = uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION);

                finalWhere =
                        NotePad.Notes._ID +
                        " = " +
                        uri.getPathSegments().
                            get(NotePad.Notes.NOTE_ID_PATH_POSITION)
                ;

                if (where !=null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                count = db.update(
                    NotePad.Notes.TABLE_NAME,
                    values,
                    finalWhere,

                    whereArgs

                );
                break;

            case CATEGORIES:
                count = db.update(NotePad.Categories.TABLE_NAME, values, where, whereArgs);
                break;

            case CATEGORY_ID:
                finalWhere = NotePad.Categories._ID + " = "
                        + uri.getPathSegments().get(NotePad.Categories.CATEGORY_ID_PATH_POSITION);
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }
                count = db.update(NotePad.Categories.TABLE_NAME, values, finalWhere, whereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    DatabaseHelper getOpenHelperForTest() {
        return mOpenHelper;
    }
}
