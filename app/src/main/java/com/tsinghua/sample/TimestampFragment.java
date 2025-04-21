package com.tsinghua.sample;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tsinghua.sample.utils.SharedViewModel;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;

public class TimestampFragment extends BottomSheetDialogFragment {
    private SharedViewModel viewModel;
    private EditText etNewLabel;
    private Button btnAddLabel, btnSaveRecords;
    private RecyclerView rvLabels, rvRecords;

    private final ArrayList<String> tagList = new ArrayList<>();
    private final ArrayList<String> recordedEvents = new ArrayList<>();
    private LabelAdapter labelAdapter;
    private RecordAdapter recordAdapter;

    private final Gson gson = new Gson();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private static final String PREFS_TAGS = "TagsPrefs";
    private static final String PREFS_EVENTS = "EventsPrefs";
    private static final String KEY_TAGS = "saved_tags_list";
    private static final String KEY_EVENTS = "saved_events_list";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_timestamp, container, false);

        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        etNewLabel = view.findViewById(R.id.et_new_label);
        btnAddLabel = view.findViewById(R.id.btn_add_label);
        btnSaveRecords = view.findViewById(R.id.btn_save_records);
        rvLabels = view.findViewById(R.id.rv_labels);
        rvRecords = view.findViewById(R.id.rv_records);

        loadTags();
        loadRecordedEvents();

        // 标签列表
        labelAdapter = new LabelAdapter(tagList);
        rvLabels.setLayoutManager(new LinearLayoutManager(getContext()));
        rvLabels.setAdapter(labelAdapter);
        new ItemTouchHelper(labelTouchHelper).attachToRecyclerView(rvLabels);

        // 记录列表
        recordAdapter = new RecordAdapter(recordedEvents);
        rvRecords.setLayoutManager(new LinearLayoutManager(getContext()));
        rvRecords.setAdapter(recordAdapter);
        new ItemTouchHelper(recordTouchHelper).attachToRecyclerView(rvRecords);
        rvRecords.setNestedScrollingEnabled(false);
        rvLabels.setNestedScrollingEnabled(false);
        View.OnTouchListener disallowInterceptTouchListener = (v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        };

        rvRecords.setOnTouchListener(disallowInterceptTouchListener);
        rvLabels.setOnTouchListener(disallowInterceptTouchListener);

        btnAddLabel.setOnClickListener(v -> {
            String label = etNewLabel.getText().toString().trim();
            if (!TextUtils.isEmpty(label) && !tagList.contains(label)) {
                tagList.add(label);
                labelAdapter.notifyItemInserted(tagList.size() - 1);
                saveTags();
                etNewLabel.setText("");
            } else {
                Toast.makeText(getContext(), "标签为空或已存在", Toast.LENGTH_SHORT).show();
            }
        });

        btnSaveRecords.setOnClickListener(v -> saveRecordsToFile());

        return view;
    }

    private final ItemTouchHelper.SimpleCallback labelTouchHelper = new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
        @Override public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
            Collections.swap(tagList, from.getAdapterPosition(), to.getAdapterPosition());
            labelAdapter.notifyItemMoved(from.getAdapterPosition(), to.getAdapterPosition());
            saveTags();
            return true;
        }
        @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
    };

    private final ItemTouchHelper.SimpleCallback recordTouchHelper = new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
        @Override public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
            Collections.swap(recordedEvents, from.getAdapterPosition(), to.getAdapterPosition());
            recordAdapter.notifyItemMoved(from.getAdapterPosition(), to.getAdapterPosition());
            saveRecordedEvents();
            return true;
        }
        @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
    };

    private void loadTags() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_TAGS, MODE_PRIVATE);
        String json = prefs.getString(KEY_TAGS, null);
        if (json != null) {
            Type type = new TypeToken<ArrayList<String>>() {}.getType();
            tagList.clear();
            tagList.addAll(gson.fromJson(json, type));
        }
    }

    private void saveTags() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_TAGS, MODE_PRIVATE);
        prefs.edit().putString(KEY_TAGS, gson.toJson(tagList)).apply();
    }

    private void loadRecordedEvents() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_EVENTS, MODE_PRIVATE);
        String json = prefs.getString(KEY_EVENTS, null);
        if (json != null) {
            Type type = new TypeToken<ArrayList<String>>() {}.getType();
            recordedEvents.clear();
            recordedEvents.addAll(gson.fromJson(json, type));
        }
    }

    private void saveRecordedEvents() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_EVENTS, MODE_PRIVATE);
        prefs.edit().putString(KEY_EVENTS, gson.toJson(recordedEvents)).apply();
    }

    private void saveRecordsToFile() {
        String id = getContext().getSharedPreferences("AppSettings", MODE_PRIVATE).getString("experiment_id", "Default");
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Sample/" + id + "/TimeStamps/");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(getContext(), "无法创建目录", Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(dir, "recorded_events_" + System.currentTimeMillis() + ".txt");
        try (FileWriter writer = new FileWriter(file)) {
            for (String line : recordedEvents) {
                writer.write(line + "\n");
            }
            Toast.makeText(getContext(), "保存成功：" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(getContext(), "保存失败", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ 清空记录
        recordedEvents.clear();
        recordAdapter.notifyDataSetChanged();
        saveRecordedEvents();
    }


    private class LabelAdapter extends RecyclerView.Adapter<LabelAdapter.ViewHolder> {
        private final List<String> tags;
        LabelAdapter(List<String> tags) { this.tags = tags; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_label, parent, false);
            return new ViewHolder(view);
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int pos) {
            String tag = tags.get(pos);
            holder.tvLabelName.setText(tag);
            holder.btnRecord.setOnClickListener(v -> {
                long now = System.currentTimeMillis();
                String formatted = timeFormat.format(new Date(now));
                int index = recordedEvents.size() + 1;
                String record = index + "：" + tag + "：" + now + "（" + formatted + "）";
                recordedEvents.add(record);
                saveRecordedEvents();
                recordAdapter.notifyItemInserted(recordedEvents.size() - 1);
            });
            holder.btnDelete.setOnClickListener(v -> {
                tags.remove(pos);
                notifyItemRemoved(pos);
                saveTags();
            });
        }

        @Override public int getItemCount() { return tags.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvLabelName;
            Button btnRecord, btnDelete;
            ViewHolder(@NonNull View view) {
                super(view);
                tvLabelName = view.findViewById(R.id.tv_label_name);
                btnRecord = view.findViewById(R.id.btn_record_timestamp);
                btnDelete = view.findViewById(R.id.btn_delete_label);
            }
        }
    }

    private static class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.ViewHolder> {
        private final List<String> records;
        RecordAdapter(List<String> records) { this.records = records; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_timestamp_label, parent, false);
            return new ViewHolder(view);
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int pos) {
            holder.textView.setText(records.get(pos));
        }

        @Override public int getItemCount() { return records.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.tv_label_name);
            }
        }
    }
}
