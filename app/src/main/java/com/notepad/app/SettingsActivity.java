package com.notepad.app;

import android.net.Uri;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 设置页：主题、主题色与数据备份
public class SettingsActivity extends AppCompatActivity {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
                    uri -> {
                        if (uri != null) {
                            exportTo(uri);
                        }
                    });

    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) {
                            importFrom(uri);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemePrefs.themeRes(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.settings);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        setupThemeOptions();
        setupColorOptions();
        setupBackupOptions();
    }

    // 主题三选一
    private void setupThemeOptions() {
        RadioGroup themeGroup = findViewById(R.id.theme_group);

        int mode = ThemePrefs.getMode(this);
        int checkedId;
        if (mode == ThemePrefs.MODE_LIGHT) {
            checkedId = R.id.theme_light;
        } else if (mode == ThemePrefs.MODE_DARK) {
            checkedId = R.id.theme_dark;
        } else {
            checkedId = R.id.theme_system;
        }
        themeGroup.check(checkedId);

        themeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.theme_light) {
                    ThemePrefs.setMode(SettingsActivity.this, ThemePrefs.MODE_LIGHT);
                } else if (checkedId == R.id.theme_dark) {
                    ThemePrefs.setMode(SettingsActivity.this, ThemePrefs.MODE_DARK);
                } else {
                    ThemePrefs.setMode(SettingsActivity.this, ThemePrefs.MODE_SYSTEM);
                }
            }
        });
    }

    // 主题色五选一
    private void setupColorOptions() {
        RadioGroup colorGroup = findViewById(R.id.color_group);

        int checkedId;
        switch (ThemePrefs.getColor(this)) {
            case ThemePrefs.COLOR_INDIGO:
                checkedId = R.id.color_indigo;
                break;
            case ThemePrefs.COLOR_TEAL:
                checkedId = R.id.color_teal;
                break;
            case ThemePrefs.COLOR_ORANGE:
                checkedId = R.id.color_orange;
                break;
            case ThemePrefs.COLOR_ROSE:
                checkedId = R.id.color_rose;
                break;
            default:
                checkedId = R.id.color_default;
                break;
        }
        colorGroup.check(checkedId);

        colorGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int color;
                if (checkedId == R.id.color_indigo) {
                    color = ThemePrefs.COLOR_INDIGO;
                } else if (checkedId == R.id.color_teal) {
                    color = ThemePrefs.COLOR_TEAL;
                } else if (checkedId == R.id.color_orange) {
                    color = ThemePrefs.COLOR_ORANGE;
                } else if (checkedId == R.id.color_rose) {
                    color = ThemePrefs.COLOR_ROSE;
                } else {
                    color = ThemePrefs.COLOR_DEFAULT;
                }
                ThemePrefs.setColor(SettingsActivity.this, color);
                recreate();
            }
        });
    }

    // 导出与恢复入口
    private void setupBackupOptions() {
        findViewById(R.id.btn_export).setOnClickListener(v ->
                exportLauncher.launch(defaultFileName()));

        findViewById(R.id.btn_import).setOnClickListener(v ->
                importLauncher.launch(new String[] { "application/json", "text/plain", "*/*" }));
    }

    private String defaultFileName() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA)
                .format(new Date());
        return "notepad-backup-" + stamp + ".json";
    }

    // 执行导出并提示结果
    private void exportTo(final Uri uri) {
        ioExecutor.execute(() -> {
            try {
                final int count = BackupManager.exportNotes(this, uri);
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.export_success, count), Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.export_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    // 执行恢复并提示结果
    private void importFrom(final Uri uri) {
        ioExecutor.execute(() -> {
            try {
                final int[] result = BackupManager.importNotes(this, uri);
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.import_success, result[0], result[1]),
                        Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.import_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }
}
