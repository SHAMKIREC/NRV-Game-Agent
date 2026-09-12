package io.nrv.gameagent;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import io.nrv.gameagent.capture.CaptureService;
import io.nrv.gameagent.input.AccessibilityDiagnostics;
import io.nrv.gameagent.input.AutomationSettings;
import io.nrv.gameagent.poker.PokerLabActivity;

public class MainActivity extends AppCompatActivity {

    private MediaProjectionManager projectionManager;
    private TextView statusView;
    private TextView diagnosticsView;
    private Button automationButton;

    private final ActivityResultLauncher<Intent> captureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    setStatus("Захват экрана не разрешён");
                    return;
                }

                Intent serviceIntent = new Intent(this, CaptureService.class);
                serviceIntent.putExtra(CaptureService.EXTRA_RESULT_CODE, result.getResultCode());
                serviceIntent.putExtra(CaptureService.EXTRA_RESULT_DATA, result.getData());

                int[] size = getScreenSize();
                serviceIntent.putExtra(CaptureService.EXTRA_WIDTH, size[0]);
                serviceIntent.putExtra(CaptureService.EXTRA_HEIGHT, size[1]);
                serviceIntent.putExtra(CaptureService.EXTRA_DENSITY, getResources().getDisplayMetrics().densityDpi);

                ContextCompat.startForegroundService(this, serviceIntent);
                setStatus("Захват активен. Агент получает кадры.");
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        requestNotificationPermissionIfNeeded();
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDiagnostics();
    }

    private View buildContent() {
        int padding = dp(20);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("NRV GAME AGENT");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Зрение → решение → AI companion");
        subtitle.setTextSize(16);
        subtitle.setPadding(0, dp(6), 0, dp(16));
        root.addView(subtitle);

        Button poker = new Button(this);
        poker.setText("POKER LAB · EQUITY");
        poker.setOnClickListener(v -> startActivity(new Intent(this, PokerLabActivity.class)));
        root.addView(poker, fullWidthParams(0));

        Button keyboardSettings = new Button(this);
        keyboardSettings.setText("AI KEYBOARD · ВКЛЮЧИТЬ В ANDROID");
        keyboardSettings.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            setStatus("Включи NRV AI Keyboard в списке клавиатур Android");
        });
        root.addView(keyboardSettings, fullWidthParams(8));

        Button keyboardPicker = new Button(this);
        keyboardPicker.setText("AI KEYBOARD · ВЫБРАТЬ КЛАВИАТУРУ");
        keyboardPicker.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        root.addView(keyboardPicker, fullWidthParams(8));

        statusView = new TextView(this);
        statusView.setText("Проверяю доступ к управлению…");
        statusView.setTextSize(17);
        statusView.setPadding(0, dp(12), 0, dp(12));
        root.addView(statusView);

        diagnosticsView = new TextView(this);
        diagnosticsView.setTextSize(14);
        diagnosticsView.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(diagnosticsView, fullWidthParams(0));

        Button accessibility = new Button(this);
        accessibility.setText("1. ОТКРЫТЬ СПЕЦИАЛЬНЫЕ ВОЗМОЖНОСТИ");
        accessibility.setOnClickListener(v -> openAccessibilitySettings());
        root.addView(accessibility, fullWidthParams(10));

        Button appInfo = new Button(this);
        appInfo.setText("ЕСЛИ СЕРОЕ — ОТКРЫТЬ НАСТРОЙКИ ПРИЛОЖЕНИЯ");
        appInfo.setOnClickListener(v -> openAppDetails());
        root.addView(appInfo, fullWidthParams(8));

        Button recheck = new Button(this);
        recheck.setText("ПЕРЕПРОВЕРИТЬ ДОСТУП");
        recheck.setOnClickListener(v -> refreshDiagnostics());
        root.addView(recheck, fullWidthParams(8));

        automationButton = new Button(this);
        automationButton.setOnClickListener(v -> toggleAutomation());
        root.addView(automationButton, fullWidthParams(12));

        Button start = new Button(this);
        start.setText("3. НАЧАТЬ РАЗРЕШЁННЫЙ ЗАХВАТ ЭКРАНА");
        start.setOnClickListener(v -> startCapture());
        root.addView(start, fullWidthParams(12));

        Button stop = new Button(this);
        stop.setText("ОСТАНОВИТЬ");
        stop.setOnClickListener(v -> {
            AutomationSettings.setEnabled(this, false);
            stopService(new Intent(this, CaptureService.class));
            refreshDiagnostics();
            setStatus("Захват и управление остановлены");
        });
        root.addView(stop, fullWidthParams(12));

        refreshDiagnostics();
        return scroll;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            setStatus("Найди «NRV Game Agent — управление» и включи службу");
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось открыть специальные возможности", Toast.LENGTH_LONG).show();
        }
    }

    private void openAppDetails() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            setStatus("На Xiaomi проверь «Разрешить запрещённые/ограниченные настройки», затем вернись сюда");
        } catch (Exception error) {
            Toast.makeText(this, "Не удалось открыть настройки приложения", Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout.LayoutParams fullWidthParams(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private void toggleAutomation() {
        AccessibilityDiagnostics.Snapshot snapshot = AccessibilityDiagnostics.read(this);
        boolean enabled = AutomationSettings.isEnabled(this);

        if (!enabled && !snapshot.serviceConnected()) {
            setStatus(snapshot.nextStep());
            Toast.makeText(this, snapshot.nextStep(), Toast.LENGTH_LONG).show();
            if (!snapshot.serviceEnabled()) openAccessibilitySettings();
            return;
        }

        AutomationSettings.setEnabled(this, !enabled);
        refreshDiagnostics();
        setStatus(!enabled ? "Управление включено" : "Управление выключено");
    }

    private void refreshDiagnostics() {
        AccessibilityDiagnostics.Snapshot snapshot = AccessibilityDiagnostics.read(this);
        if (diagnosticsView != null) diagnosticsView.setText(snapshot.summary() + "\n\n" + snapshot.nextStep());
        if (automationButton != null) {
            boolean enabled = AutomationSettings.isEnabled(this);
            automationButton.setText(enabled
                    ? "2. УПРАВЛЕНИЕ: ВКЛЮЧЕНО"
                    : snapshot.serviceConnected() ? "2. ВКЛЮЧИТЬ УПРАВЛЕНИЕ" : "2. УПРАВЛЕНИЕ: НУЖЕН ДОСТУП");
        }
        if (snapshot.serviceConnected()) setStatus("Доступ к управлению работает");
        else setStatus(snapshot.nextStep());
    }

    private int[] getScreenSize() {
        if (Build.VERSION.SDK_INT >= 30) {
            Rect bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
            return new int[]{bounds.width(), bounds.height()};
        }
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        return new int[]{metrics.widthPixels, metrics.heightPixels};
    }

    private void startCapture() {
        if (projectionManager == null) {
            Toast.makeText(this, "MediaProjection недоступен", Toast.LENGTH_LONG).show();
            return;
        }
        captureLauncher.launch(projectionManager.createScreenCaptureIntent());
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void setStatus(String text) { if (statusView != null) statusView.setText(text); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
