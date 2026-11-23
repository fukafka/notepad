package com.notepad.app;

import android.net.Uri;
import android.provider.BaseColumns;

// 表名、列名、URI、MIME
public final class NotePad {
    public static final String AUTHORITY = "com.google.provider.NotePad";

    private NotePad() {
    }

    public static final class Notes implements BaseColumns {

        private Notes() {}

        public static final String TABLE_NAME = "notes";

        private static final String SCHEME = "content://";

        private static final String PATH_NOTES = "/notes";

        private static final String PATH_NOTE_ID = "/notes/";

        public static final int NOTE_ID_PATH_POSITION = 1;

        private static final String PATH_LIVE_FOLDER = "/live_folders/notes";

        public static final Uri CONTENT_URI =  Uri.parse(SCHEME + AUTHORITY + PATH_NOTES);

        public static final Uri CONTENT_ID_URI_BASE
            = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID);

        public static final Uri CONTENT_ID_URI_PATTERN
            = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID + "/#");

        public static final Uri LIVE_FOLDER_URI
            = Uri.parse(SCHEME + AUTHORITY + PATH_LIVE_FOLDER);

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.note";

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.note";

        public static final String DEFAULT_SORT_ORDER = "pinned DESC, modified DESC";

        public static final String COLUMN_NAME_TITLE = "title";

        public static final String COLUMN_NAME_NOTE = "note";

        public static final String COLUMN_NAME_CREATE_DATE = "created";

        public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";

        public static final String COLUMN_NAME_CATEGORY = "category";

        public static final String COLUMN_NAME_IS_FAVORITE = "is_favorite";

        public static final String COLUMN_NAME_DELETED_AT = "deleted_at";

        public static final String COLUMN_NAME_IS_TODO = "is_todo";

        public static final String COLUMN_NAME_IS_DONE = "is_done";

        public static final String COLUMN_NAME_PINNED = "pinned";

        public static final String CATEGORY_DEFAULT = "默认";
        public static final String CATEGORY_WORK = "工作";
        public static final String CATEGORY_STUDY = "学习";
        public static final String CATEGORY_LIFE = "生活";

        public static final String[] CATEGORIES = {
                CATEGORY_DEFAULT, CATEGORY_WORK, CATEGORY_STUDY, CATEGORY_LIFE
        };
    }

    public static final class Categories implements BaseColumns {

        private Categories() {
        }

        public static final String TABLE_NAME = "categories";

        private static final String SCHEME = "content://";
        private static final String PATH_CATEGORIES = "/categories";
        private static final String PATH_CATEGORY_ID = "/categories/";

        public static final int CATEGORY_ID_PATH_POSITION = 1;

        public static final Uri CONTENT_URI = Uri.parse(SCHEME + AUTHORITY + PATH_CATEGORIES);

        public static final Uri CONTENT_ID_URI_BASE =
                Uri.parse(SCHEME + AUTHORITY + PATH_CATEGORY_ID);

        public static final String CONTENT_TYPE =
                "vnd.android.cursor.dir/vnd.google.category";

        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/vnd.google.category";

        public static final String DEFAULT_SORT_ORDER = "sort_order ASC, name ASC";

        public static final String COLUMN_NAME_NAME = "name";

        public static final String COLUMN_NAME_SORT_ORDER = "sort_order";
    }
}
