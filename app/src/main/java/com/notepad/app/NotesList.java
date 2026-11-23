package com.notepad.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

// 笔记列表
// 搜索、分类筛选、待办、回收站
@SuppressWarnings("deprecation")
public class NotesList extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String EXTRA_TRASH = "trash_mode";

    public static final String EXTRA_PASTE_TEXT = "paste_text";
    public static final String EXTRA_PASTE_TITLE = "paste_title";

    public static final String EXTRA_TODO = "todo_mode";

    private static final int LOADER_NOTES = 1;
    private static final int LOADER_CATEGORIES = 2;

    private NoteListAdapter adapter;
    private RecyclerView noteListView;
    private View emptyView;
    private TextView emptyText;
    private ChipGroup chipGroup;
    private Chip chipAll;
    private Chip chipFavorite;
    private Chip chipTodo;
    private boolean rebuildingChips;

    private boolean trashMode;
    private boolean favoriteOnly;
    private boolean todoOnly;
    private String selectedCategory;
    private String keyword = "";

    private int appliedColor;

    // 初始化工具栏、列表、搜索与筛选
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        appliedColor = ThemePrefs.getColor(this);
        setTheme(ThemePrefs.themeRes(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes_list);

        trashMode = getIntent().getBooleanExtra(EXTRA_TRASH, false);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (trashMode && getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_trash);
        }

        noteListView = findViewById(R.id.notes_list);
        emptyView = findViewById(R.id.empty_view);
        emptyText = findViewById(R.id.empty_text);
        chipGroup = findViewById(R.id.chip_group);

        noteListView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NoteListAdapter(new NoteListAdapter.OnNoteActionListener() {
            @Override
            public void onNoteClick(long id, View anchor) {
                if (trashMode) {
                    showTrashMenu(id, anchor);
                } else {
                    openNote(id);
                }
            }

            @Override
            public void onNoteLongClick(long id, boolean favorite, boolean pinned, View anchor) {
                if (trashMode) {
                    showTrashMenu(id, anchor);
                } else {
                    showNoteMenu(id, favorite, pinned, anchor);
                }
            }

            @Override
            public void onTodoChecked(long id, boolean done) {
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_IS_DONE, done ? 1 : 0);
                getContentResolver().update(noteUri(id), values, null, null);
            }
        });
        noteListView.setAdapter(adapter);

        SearchView searchView = findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                keyword = newText == null ? "" : newText.trim();
                adapter.setKeyword(keyword);
                reloadNotes();
                return true;
            }
        });

        if (trashMode) {

            findViewById(R.id.chip_scroll).setVisibility(View.GONE);
        } else {
            chipGroup.setOnCheckedChangeListener(new ChipGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(ChipGroup group, int checkedId) {
                    if (rebuildingChips) {
                        return;
                    }
                    View checked = group.findViewById(checkedId);
                    favoriteOnly = checked == chipFavorite;
                    todoOnly = checked == chipTodo;
                    selectedCategory = (!favoriteOnly && !todoOnly && checked != null
                            && checked.getTag() instanceof String)
                            ? (String) checked.getTag()
                            : null;
                    reloadNotes();
                }
            });
            getSupportLoaderManager().initLoader(LOADER_CATEGORIES, null, categoryCallbacks);
        }

        getSupportLoaderManager().initLoader(LOADER_NOTES, null, this);
    }

    // 从设置页返回时，主题色变了就重建界面
    @Override
    protected void onResume() {
        super.onResume();
        if (appliedColor != ThemePrefs.getColor(this)) {
            recreate();
        }
    }

    // 条件变化后重新加载列表
    private void reloadNotes() {
        getSupportLoaderManager().restartLoader(LOADER_NOTES, null, this);
    }

    private final LoaderManager.LoaderCallbacks<Cursor> categoryCallbacks =
            new LoaderManager.LoaderCallbacks<Cursor>() {
                // 分类查询：结果用于重建筛选 Chip
                @NonNull
                @Override
                public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
                    return new CursorLoader(
                            NotesList.this,
                            NotePad.Categories.CONTENT_URI,
                            new String[] { NotePad.Categories.COLUMN_NAME_NAME },
                            null, null,
                            NotePad.Categories.DEFAULT_SORT_ORDER);
                }

                // 分类查询完成：重建 Chip
                @Override
                public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
                    rebuildChips(data);
                }

                @Override
                public void onLoaderReset(@NonNull Loader<Cursor> loader) {

                }
            };

    // 依据分类表重建筛选 Chip
    private void rebuildChips(Cursor categories) {
        rebuildingChips = true;
        chipGroup.removeAllViews();

        Chip addChip = newChip();
        addChip.setText("+");
        addChip.setCheckable(false);
        addChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNewCategoryDialog();
            }
        });
        chipGroup.addView(addChip);

        chipAll = newChip();
        chipAll.setText(R.string.chip_all);
        chipGroup.addView(chipAll);

        chipFavorite = newChip();
        chipFavorite.setText(R.string.chip_favorite);
        chipGroup.addView(chipFavorite);

        chipTodo = newChip();
        chipTodo.setText(R.string.chip_todo);
        chipGroup.addView(chipTodo);

        if (categories != null) {

            categories.moveToPosition(-1);
            int nameIndex = categories.getColumnIndex(NotePad.Categories.COLUMN_NAME_NAME);
            while (categories.moveToNext()) {
                Chip chip = newChip();
                String name = categories.getString(nameIndex);
                chip.setText(name);
                chip.setTag(name);
                chipGroup.addView(chip);
            }
        }

        if (todoOnly) {
            chipTodo.setChecked(true);
        } else if (favoriteOnly) {
            chipFavorite.setChecked(true);
        } else if (selectedCategory != null) {
            Chip target = findChipByTag(selectedCategory);
            if (target != null) {
                target.setChecked(true);
            } else {
                chipAll.setChecked(true);
                selectedCategory = null;
            }
        } else {
            chipAll.setChecked(true);
        }

        rebuildingChips = false;
    }

    private Chip newChip() {
        return (Chip) LayoutInflater.from(this).inflate(R.layout.item_chip, chipGroup, false);
    }

    private Chip findChipByTag(String name) {
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View child = chipGroup.getChildAt(i);
            if (name.equals(child.getTag())) {
                return (Chip) child;
            }
        }
        return null;
    }

    // 弹窗输入名称并新增分类
    private void showNewCategoryDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.category_name_hint);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.new_category)
                .setView(input)
                .setPositiveButton(R.string.dialog_confirm, null)
                .setNegativeButton(R.string.dialog_cancel, null)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                String name = input.getText().toString().trim();
                                if (name.isEmpty()) {
                                    return;
                                }
                                if (name.equals(getString(R.string.chip_all))
                                        || name.equals(getString(R.string.chip_favorite))) {
                                    Toast.makeText(NotesList.this, R.string.category_reserved,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                if (categoryExists(name)) {
                                    Toast.makeText(NotesList.this, R.string.category_exists,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                ContentValues values = new ContentValues();
                                values.put(NotePad.Categories.COLUMN_NAME_NAME, name);
                                try {
                                    getContentResolver().insert(
                                            NotePad.Categories.CONTENT_URI, values);
                                } catch (Exception e) {
                                    Toast.makeText(NotesList.this, R.string.category_exists,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                selectedCategory = name;
                                favoriteOnly = false;
                                reloadNotes();
                                dialog.dismiss();
                            }
                        });
            }
        });
        dialog.show();
    }

    private boolean categoryExists(String name) {
        Cursor cursor = getContentResolver().query(
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

    // 列表查询：把搜索、分类、收藏、待办、回收站条件组合成一次查询
    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
        List<String> clauses = new ArrayList<>();
        List<String> selectionArgs = new ArrayList<>();

        clauses.add(trashMode
                ? NotePad.Notes.COLUMN_NAME_DELETED_AT + " > 0"
                : NotePad.Notes.COLUMN_NAME_DELETED_AT + " = 0");

        if (!trashMode && favoriteOnly) {
            clauses.add(NotePad.Notes.COLUMN_NAME_IS_FAVORITE + " = 1");
        }
        if (!trashMode && todoOnly) {
            clauses.add(NotePad.Notes.COLUMN_NAME_IS_TODO + " = 1");
        }
        if (!trashMode && selectedCategory != null) {
            clauses.add(NotePad.Notes.COLUMN_NAME_CATEGORY + " = ?");
            selectionArgs.add(selectedCategory);
        }
        if (!keyword.isEmpty()) {

            clauses.add("(" + NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR "
                    + NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?)");
            String like = "%" + keyword + "%";
            selectionArgs.add(like);
            selectionArgs.add(like);
        }

        String selection = TextUtils.join(" AND ", clauses);
        String sortOrder = trashMode
                ? NotePad.Notes.COLUMN_NAME_DELETED_AT + " DESC"
                : NotePad.Notes.DEFAULT_SORT_ORDER;

        return new CursorLoader(
                this,
                NotePad.Notes.CONTENT_URI,
                NoteListAdapter.PROJECTION,
                selection,
                selectionArgs.isEmpty() ? null : selectionArgs.toArray(new String[0]),
                sortOrder);
    }

    // 笔记查询完成：交给适配器并更新空态
    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        adapter.swapCursor(data);
        updateEmptyState(data == null ? 0 : data.getCount());
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        adapter.swapCursor(null);
    }

    private void updateEmptyState(int count) {
        boolean empty = count == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        noteListView.setVisibility(empty ? View.GONE : View.VISIBLE);

        int textRes;
        if (trashMode) {
            textRes = R.string.empty_trash;
        } else if (!keyword.isEmpty()) {
            textRes = R.string.empty_search;
        } else {
            textRes = R.string.empty_notes;
        }
        emptyText.setText(textRes);
    }

    private Uri noteUri(long id) {
        return ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, id);
    }

    // 打开笔记编辑器
    private void openNote(long id) {
        Uri uri = noteUri(id);
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            setResult(RESULT_OK, new Intent().setData(uri));
            finish();
        } else {
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
        }
    }

    // 长按菜单：收藏、置顶、复制、删除
    private void showNoteMenu(final long id, boolean favorite, boolean pinned, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.list_context_menu);

        MenuItem favoriteItem = popup.getMenu().findItem(R.id.context_favorite);
        if (favoriteItem != null) {
            favoriteItem.setTitle(favorite ? R.string.menu_unfavorite : R.string.menu_favorite);
        }

        MenuItem pinItem = popup.getMenu().findItem(R.id.context_pin);
        if (pinItem != null) {
            pinItem.setTitle(pinned ? R.string.menu_unpin : R.string.menu_pin);
        }

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.context_open) {
                    openNote(id);
                    return true;
                } else if (itemId == R.id.context_copy) {
                    copyNote(id);
                    return true;
                } else if (itemId == R.id.context_favorite) {
                    toggleFavorite(id);
                    return true;
                } else if (itemId == R.id.context_pin) {
                    togglePinned(id);
                    return true;
                } else if (itemId == R.id.context_delete) {
                    moveToTrash(id);
                    return true;
                }
                return false;
            }
        });
        popup.show();
    }

    // 回收站菜单：恢复、彻底删除
    private void showTrashMenu(final long id, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.menu_trash_item);
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.context_restore) {
                    restoreNote(id);
                    return true;
                } else if (itemId == R.id.context_delete_forever) {
                    confirmDeleteForever(id);
                    return true;
                }
                return false;
            }
        });
        popup.show();
    }

    // 切换收藏状态
    private void toggleFavorite(long id) {
        Cursor cursor = getContentResolver().query(
                noteUri(id),
                new String[] { NotePad.Notes.COLUMN_NAME_IS_FAVORITE },
                null, null, null);
        if (cursor == null) {
            return;
        }
        int favorite = 0;
        try {
            if (cursor.moveToFirst()) {
                favorite = cursor.getInt(0) == 1 ? 0 : 1;
            }
        } finally {
            cursor.close();
        }

        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_IS_FAVORITE, favorite);
        getContentResolver().update(noteUri(id), values, null, null);
    }

    // 切换置顶状态
    private void togglePinned(long id) {
        Cursor cursor = getContentResolver().query(
                noteUri(id),
                new String[] { NotePad.Notes.COLUMN_NAME_PINNED },
                null, null, null);
        if (cursor == null) {
            return;
        }
        int pinned = 0;
        try {
            if (cursor.moveToFirst()) {
                pinned = cursor.getInt(0) == 1 ? 0 : 1;
            }
        } finally {
            cursor.close();
        }

        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_PINNED, pinned);
        getContentResolver().update(noteUri(id), values, null, null);
    }

    // 把笔记 URI 放入剪贴板
    private void copyNote(long id) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newUri(getContentResolver(), "Note", noteUri(id)));
    }

    // 在列表页读取剪贴板（此时有焦点），再交给编辑器
    private void pasteFromClipboard() {
        Intent intent = new Intent(Intent.ACTION_PASTE, NotePad.Notes.CONTENT_URI);

        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();

        if (clip != null && clip.getItemCount() > 0) {
            ClipData.Item item = clip.getItemAt(0);
            Uri uri = item.getUri();

            if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(
                    getContentResolver().getType(uri))) {
                Cursor cursor = getContentResolver().query(
                        uri,
                        new String[] {
                                NotePad.Notes.COLUMN_NAME_TITLE,
                                NotePad.Notes.COLUMN_NAME_NOTE
                        },
                        null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            intent.putExtra(EXTRA_PASTE_TITLE, cursor.getString(0));
                            intent.putExtra(EXTRA_PASTE_TEXT, cursor.getString(1));
                        }
                    } finally {
                        cursor.close();
                    }
                }
            } else {

                CharSequence text = item.coerceToText(this);
                if (text != null) {
                    intent.putExtra(EXTRA_PASTE_TEXT, text.toString());
                }
            }
        }

        if (!intent.hasExtra(EXTRA_PASTE_TEXT)) {
            Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        startActivity(intent);
    }

    // 软删除：移入最近删除
    private void moveToTrash(long id) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_DELETED_AT, System.currentTimeMillis());
        getContentResolver().update(noteUri(id), values, null, null);
        Toast.makeText(this, R.string.moved_to_trash, Toast.LENGTH_SHORT).show();
    }

    // 从回收站恢复
    private void restoreNote(long id) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_DELETED_AT, 0);
        getContentResolver().update(noteUri(id), values, null, null);
        Toast.makeText(this, R.string.restored, Toast.LENGTH_SHORT).show();
    }

    // 二次确认后物理删除
    private void confirmDeleteForever(final long id) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.context_delete_forever)
                .setMessage(R.string.dialog_delete_forever_message)
                .setPositiveButton(R.string.dialog_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getContentResolver().delete(noteUri(id), null, null);
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    // 二次确认后清空回收站
    private void confirmEmptyTrash() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_empty_trash)
                .setMessage(R.string.dialog_empty_trash_message)
                .setPositiveButton(R.string.dialog_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getContentResolver().delete(NotePad.Notes.CONTENT_URI,
                                NotePad.Notes.COLUMN_NAME_DELETED_AT + " > 0", null);
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(trashMode ? R.menu.menu_trash : R.menu.list_options_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem pasteItem = menu.findItem(R.id.menu_paste);
        if (pasteItem != null) {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                pasteItem.setEnabled(clipboard.hasPrimaryClip());
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    // 菜单入口：新建、新建待办、粘贴、最近删除、设置
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_add) {
            startActivity(new Intent(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI));
            return true;
        } else if (itemId == R.id.menu_add_todo) {
            startActivity(new Intent(Intent.ACTION_INSERT, NotePad.Notes.CONTENT_URI)
                    .putExtra(EXTRA_TODO, true));
            return true;
        } else if (itemId == R.id.menu_paste) {
            pasteFromClipboard();
            return true;
        } else if (itemId == R.id.menu_trash) {
            startActivity(new Intent(this, NotesList.class).putExtra(EXTRA_TRASH, true));
            return true;
        } else if (itemId == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (itemId == R.id.menu_empty_trash) {
            confirmEmptyTrash();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
