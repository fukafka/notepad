package com.notepad.app;

import static com.notepad.app.R.*;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.List;

//编辑、保存、分类、收藏、删除
public class NoteEditor extends AppCompatActivity {

    private static final String TAG = "NoteEditor";

    private static final String[] PROJECTION =
        new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_CATEGORY,
            NotePad.Notes.COLUMN_NAME_IS_FAVORITE,
            NotePad.Notes.COLUMN_NAME_IS_TODO,
            NotePad.Notes.COLUMN_NAME_IS_DONE
    };

    private static final String ORIGINAL_CONTENT = "origContent";

    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private Spinner mCategory;
    private ArrayAdapter<String> mCategoryAdapter;
    private final List<String> mCategoryNames = new ArrayList<>();
    private boolean mIsFavorite;
    private boolean mIsTodo;
    private boolean mIsDone;
    private boolean mPastePending;
    private String mOriginalContent;

    // 带横线的编辑框，横线颜色跟随主题
    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);

            android.content.res.TypedArray a = context.obtainStyledAttributes(
                    new int[] { android.R.attr.textColorSecondary });
            int lineColor = a.getColor(0, 0xFF808080);
            a.recycle();
            mPaint.setColor((lineColor & 0x00FFFFFF) | 0x33000000);
        }

        @Override
        protected void onDraw(Canvas canvas) {

            int count = getLineCount();

            Rect r = mRect;
            Paint paint = mPaint;

            for (int i = 0; i < count; i++) {

                int baseline = getLineBounds(i, r);

                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            super.onDraw(canvas);
        }
    }

    // 解析 Intent：新建或编辑已有笔记
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemePrefs.themeRes(this));
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();

        final String action = intent.getAction();

        if (Intent.ACTION_EDIT.equals(action)) {

            mState = STATE_EDIT;
            mUri = intent.getData();

        } else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action)) {

            mState = STATE_INSERT;
            mUri = null;

        } else {

            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        if (mUri == null) {

            MatrixCursor draft = new MatrixCursor(new String[] {
                    NotePad.Notes._ID,
                    NotePad.Notes.COLUMN_NAME_TITLE,
                    NotePad.Notes.COLUMN_NAME_NOTE,
                    NotePad.Notes.COLUMN_NAME_CATEGORY,
                    NotePad.Notes.COLUMN_NAME_IS_FAVORITE
            });
            draft.addRow(new Object[] { -1L, "", "", NotePad.Notes.CATEGORY_DEFAULT, 0 });
            draft.moveToFirst();
            mCursor = draft;
        } else {
            mCursor = managedQuery(
                mUri,
                PROJECTION,
                null,
                null,
                null
            );
        }

        setContentView(R.layout.note_editor);

        Toolbar toolbar = findViewById(R.id.editor_toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mText = (EditText) findViewById(R.id.note);

        mCategory = (Spinner) findViewById(R.id.category_spinner);
        mCategoryNames.addAll(loadCategoryNames());
        mCategoryAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                mCategoryNames);
        mCategoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCategory.setAdapter(mCategoryAdapter);

        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }

        if (Intent.ACTION_PASTE.equals(action)) {
            String pastedText = getIntent().getStringExtra(NotesList.EXTRA_PASTE_TEXT);
            if (pastedText != null) {

                mText.setText(pastedText);
            } else {
                mPastePending = true;
            }
        }

        mIsTodo = getIntent().getBooleanExtra(NotesList.EXTRA_TODO, false);
    }

    // 回填标题、正文、分类与收藏状态
    @Override
    protected void onResume() {
        super.onResume();

        if (mCursor != null) {

            boolean isDraft = mCursor instanceof MatrixCursor;

            if (!isDraft) {
                mCursor.requery();
            }

            mCursor.moveToFirst();

            if (mState == STATE_EDIT) {

                int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                String title = mCursor.getString(colTitleIndex);
                Resources res = getResources();
                String text = String.format(res.getString(R.string.title_edit), title);
                setToolbarTitle(text);

            } else if (mState == STATE_INSERT) {
                setToolbarTitle(getText(mIsTodo
                        ? R.string.title_create_todo
                        : R.string.title_create));
            }

            if (!isDraft) {

                int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                String note = mCursor.getString(colNoteIndex);
                mText.setTextKeepState(note);

                if (mOriginalContent == null) {
                    mOriginalContent = note;
                }

                int colCategory = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CATEGORY);
                String category = mCursor.getString(colCategory);
                int categoryIndex = mCategoryNames.indexOf(category);
                if (categoryIndex < 0) {

                    mCategoryNames.add(category);
                    mCategoryAdapter.notifyDataSetChanged();
                    categoryIndex = mCategoryNames.size() - 1;
                }
                mCategory.setSelection(categoryIndex);

                int colFavorite = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_IS_FAVORITE);
                mIsFavorite = mCursor.getInt(colFavorite) == 1;

                int colTodo = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_IS_TODO);
                int colDone = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_IS_DONE);
                mIsTodo = mCursor.getInt(colTodo) == 1;
                mIsDone = mCursor.getInt(colDone) == 1;
            }

            invalidateOptionsMenu();

        } else {
            setToolbarTitle(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
        }
    }

    private void setToolbarTitle(CharSequence title) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
        } else {
            setTitle(title);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {

        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && mPastePending) {
            mPastePending = false;
            performPaste();
        }
    }

    // 离开时保存，草稿在这里首次落库
    @Override
    protected void onPause() {
        super.onPause();

        if (mCursor != null) {

            String text = mText.getText().toString();

            if (mState == STATE_EDIT) {

                updateNote(text, null);
            } else if (mState == STATE_INSERT) {

                updateNote(text, null);
                mState = STATE_EDIT;
          }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);

        if (mState == STATE_EDIT) {

            Intent intent = new Intent(null, mUri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                    new ComponentName(this, NoteEditor.class), null, intent, 0, null);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
        String savedNote = mCursor.getString(colNoteIndex);
        String currentNote = mText.getText().toString();
        if (savedNote.equals(currentNote)) {
            menu.findItem(R.id.menu_revert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_revert).setVisible(true);
        }

        MenuItem favoriteItem = menu.findItem(R.id.menu_favorite);
        if (favoriteItem != null) {
            favoriteItem.setTitle(mIsFavorite ? R.string.menu_unfavorite : R.string.menu_favorite);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();
        if (id == android.R.id.home) {

            finish();
        } else if (id == R.id.menu_favorite) {
            toggleFavorite();
        } else if(id== R.id.menu_save) {
            String text = mText.getText().toString();
            updateNote(text, null);
            finish();
        } else if (id == R.id.menu_delete) {
            deleteNote();
            finish();
        } else if (id == R.id.menu_revert) {
            cancelNote();
        }
        return super.onOptionsItemSelected(item);
    }

    // 处理剪贴板内容
    private final void performPaste() {

        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        ContentResolver cr = getContentResolver();

        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {

            String text=null;
            String title=null;

            ClipData.Item item = clip.getItemAt(0);

            Uri uri = item.getUri();

            if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {

                Cursor orig = cr.query(
                        uri,
                        PROJECTION,
                        null,
                        null,
                        null
                );

                if (orig != null) {
                    if (orig.moveToFirst()) {
                        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                        int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                        text = orig.getString(colNoteIndex);
                        title = orig.getString(colTitleIndex);
                    }

                    orig.close();
                }
            }

            if (text == null) {
                text = item.coerceToText(this).toString();
            }

            mText.setText(text);
        }
    }

    // 写库：新建用 insert，已有用 update
    private final void updateNote(String text, String title) {

        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());

        if (mState == STATE_INSERT) {

            if (title == null) {
                title = text == null ? "" : text.trim();

                int lineEnd = title.indexOf('\n');
                if (lineEnd > 0) {
                    title = title.substring(0, lineEnd).trim();
                }

                if (title.isEmpty()) {
                    title = getString(R.string.untitled_note);
                } else if (title.length() > 30) {
                    title = title.substring(0, 30).trim();
                }
            }

            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        } else if (title != null) {

            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        }

        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);

        if (mCategory != null && mCategory.getSelectedItem() != null) {
            values.put(NotePad.Notes.COLUMN_NAME_CATEGORY,
                    mCategory.getSelectedItem().toString());
        }

        values.put(NotePad.Notes.COLUMN_NAME_IS_FAVORITE, mIsFavorite ? 1 : 0);
        values.put(NotePad.Notes.COLUMN_NAME_IS_TODO, mIsTodo ? 1 : 0);
        values.put(NotePad.Notes.COLUMN_NAME_IS_DONE, mIsDone ? 1 : 0);

        if (mUri == null) {

            mUri = getContentResolver().insert(NotePad.Notes.CONTENT_URI, values);
            if (mUri != null) {
                setResult(RESULT_OK, new Intent().setAction(mUri.toString()));

                if (mCursor != null) {
                    mCursor.close();
                }
                mCursor = managedQuery(mUri, PROJECTION, null, null, null);
                if (mCursor != null) {
                    mCursor.moveToFirst();
                }
            } else {
                Log.e(TAG, "Failed to insert new note");
            }
        } else {
            getContentResolver().update(
                mUri,
                values,
                null,
                null
            );
        }

    }

    // 放弃修改并关闭
    private final void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {

                mCursor.close();
                mCursor = null;
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
                getContentResolver().update(mUri, values, null, null);
            } else if (mState == STATE_INSERT) {

                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    // 读取分类下拉的数据源
    private List<String> loadCategoryNames() {
        List<String> names = new ArrayList<>();
        Cursor cursor = getContentResolver().query(
                NotePad.Categories.CONTENT_URI,
                new String[] { NotePad.Categories.COLUMN_NAME_NAME },
                null, null,
                NotePad.Categories.DEFAULT_SORT_ORDER);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    names.add(cursor.getString(0));
                }
            } finally {
                cursor.close();
            }
        }
        if (names.isEmpty()) {
            names.add(NotePad.Notes.CATEGORY_DEFAULT);
        }
        return names;
    }

    // 切换当前笔记的收藏状态
    private void toggleFavorite() {
        mIsFavorite = !mIsFavorite;
        if (mUri != null) {
            ContentValues values = new ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_IS_FAVORITE, mIsFavorite ? 1 : 0);
            getContentResolver().update(mUri, values, null, null);
        }
        invalidateOptionsMenu();
    }

    // 删除当前笔记，已有的移入回收站
    private final void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
        if (mUri != null) {
            ContentValues values = new ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_DELETED_AT, System.currentTimeMillis());
            getContentResolver().update(mUri, values, null, null);
        }
        mText.setText("");
    }
}
