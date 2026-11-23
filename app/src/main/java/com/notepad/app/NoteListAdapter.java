package com.notepad.app;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;

import java.util.Locale;

// 把查询结果绑定成列表卡片
public class NoteListAdapter extends RecyclerView.Adapter<NoteListAdapter.NoteViewHolder> {

    public static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
            NotePad.Notes.COLUMN_NAME_CATEGORY,
            NotePad.Notes.COLUMN_NAME_IS_FAVORITE,
            NotePad.Notes.COLUMN_NAME_DELETED_AT,
            NotePad.Notes.COLUMN_NAME_IS_TODO,
            NotePad.Notes.COLUMN_NAME_IS_DONE,
            NotePad.Notes.COLUMN_NAME_PINNED,
    };

    public interface OnNoteActionListener {
        void onNoteClick(long id, View anchor);

        void onNoteLongClick(long id, boolean favorite, boolean pinned, View anchor);

        void onTodoChecked(long id, boolean done);
    }

    private final OnNoteActionListener listener;
    private Cursor cursor;
    private String keyword = "";

    public NoteListAdapter(OnNoteActionListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    // 记下搜索词，绑定卡片时高亮命中片段
    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? "" : keyword.trim();
    }

    // 替换数据源
    public void swapCursor(Cursor newCursor) {
        cursor = newCursor;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return cursor == null ? 0 : cursor.getCount();
    }

    @Override
    public long getItemId(int position) {
        if (cursor != null && cursor.moveToPosition(position)) {
            return cursor.getLong(0);
        }
        return RecyclerView.NO_ID;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    // 绑定标题、摘要、分类、收藏、待办与时间
    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        if (cursor == null || !cursor.moveToPosition(position)) {
            return;
        }

        Context context = holder.itemView.getContext();
        String title = cursor.getString(1);
        String note = cursor.getString(2);
        long modified = cursor.getLong(3);
        String category = cursor.getString(4);
        boolean favorite = cursor.getInt(5) == 1;
        long deletedAt = cursor.getLong(6);
        boolean todo = cursor.getInt(7) == 1;
        boolean done = cursor.getInt(8) == 1;
        boolean pinned = cursor.getInt(9) == 1;

        int highlightColor = MaterialColors.getColor(holder.title,
                com.google.android.material.R.attr.colorPrimary);
        String shownTitle = TextUtils.isEmpty(title)
                ? context.getString(R.string.untitled_note)
                : title;
        holder.title.setText(highlight(shownTitle, highlightColor));

        String preview = "";
        if (note != null) {
            int lineEnd = note.indexOf('\n');
            preview = lineEnd >= 0 ? note.substring(lineEnd + 1) : "";
            preview = preview.replace('\n', ' ').trim();
        }
        holder.preview.setText(highlight(preview, highlightColor));
        holder.preview.setVisibility(preview.isEmpty() ? View.GONE : View.VISIBLE);

        holder.category.setText(TextUtils.isEmpty(category)
                ? context.getString(R.string.category_default)
                : category);
        holder.star.setVisibility(favorite ? View.VISIBLE : View.GONE);
        holder.pinTag.setVisibility(pinned ? View.VISIBLE : View.GONE);

        long shownTime = deletedAt > 0 ? deletedAt : modified;
        holder.time.setText(TimeFormatter.format(context, shownTime));

        final long id = cursor.getLong(0);

        holder.done.setOnCheckedChangeListener(null);
        holder.done.setVisibility(todo ? View.VISIBLE : View.GONE);
        holder.todoTag.setVisibility(todo ? View.VISIBLE : View.GONE);
        if (todo) {
            holder.done.setChecked(done);
            holder.done.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    listener.onTodoChecked(id, isChecked);
                }
            });
        }
        if (todo && done) {
            holder.title.setPaintFlags(
                    holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.title.setAlpha(0.55f);
        } else {
            holder.title.setPaintFlags(
                    holder.title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.title.setAlpha(1f);
        }
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onNoteClick(id, v);
            }
        });
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                listener.onNoteLongClick(id, favorite, pinned, v);
                return true;
            }
        });
    }

    // 给搜索词命中的片段染色并加粗
    private CharSequence highlight(String text, int color) {
        if (TextUtils.isEmpty(text) || keyword.isEmpty()) {
            return text;
        }
        String lowerText = text.toLowerCase(Locale.getDefault());
        String lowerKey = keyword.toLowerCase(Locale.getDefault());
        SpannableString span = new SpannableString(text);
        int start = 0;
        while (true) {
            int index = lowerText.indexOf(lowerKey, start);
            if (index < 0) {
                break;
            }
            int end = index + keyword.length();
            span.setSpan(new ForegroundColorSpan(color), index, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new StyleSpan(Typeface.BOLD), index, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = end;
        }
        return span;
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView preview;
        final TextView category;
        final TextView star;
        final TextView time;
        final TextView todoTag;
        final TextView pinTag;
        final MaterialCheckBox done;

        NoteViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.note_title);
            preview = itemView.findViewById(R.id.note_preview);
            category = itemView.findViewById(R.id.note_category);
            star = itemView.findViewById(R.id.note_star);
            time = itemView.findViewById(R.id.note_time);
            todoTag = itemView.findViewById(R.id.note_todo_tag);
            pinTag = itemView.findViewById(R.id.note_pin_tag);
            done = itemView.findViewById(R.id.note_done);
        }
    }
}
