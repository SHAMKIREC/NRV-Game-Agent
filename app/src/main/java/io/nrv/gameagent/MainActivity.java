package io.nrv.gameagent;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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
import io.nrv.gameagent.keyboard.CompanionModeStore;
import io.nrv.gameagent.keyboard.ScreenInsightStore;
import io.nrv.gameagent.poker.PokerLabActivity;

public class MainActivity extends AppCompatActivity {

    private MediaProjectionManager projectionManager;
    private TextView statusView;
    private boolean pendingCaptureAfterOverlay = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private View permissionPreview;

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
                setStatus("Захват запущен. Если разрешение «поверх приложений» активно, панель NRV появится сразу.");
            });

    private final ActivityResultLauncher<Intent> overlayLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> resumeAfterOverlayPermission());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingCaptureAfterOverlay && Settings.canDrawOverlays(this)) {
            resumeAfterOverlayPermission();
        }
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        removePermissionPreview();
        super.onDestroy();
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
        title.setText("NRV AI KEYBOARD");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Клавиатура + анализ экрана + плавающая панель");
        subtitle.setTextSize(16);
        subtitle.setPadding(0, dp(6), 0, dp(16));
        root.addView(subtitle);

        TextView setup = new TextView(this);
        setup.setText("1) Разреши панель поверх приложений\n2) Нажми ЗАПУСТИТЬ АНАЛИЗ\n3) NRV сначала покажет тестовую зелёную панель\n4) Разреши захват экрана\n5) Вернись в игру");
        setup.setTextSize(16);
        setup.setPadding(dp(8), dp(8), dp(8), dp(14));
        root.addView(setup, fullWidthParams(0));

        Button keyboardSettings = new Button(this);
        keyboardSettings.setText("1. ВКЛЮЧИТЬ NRV AI KEYBOARD");
        keyboardSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(keyboardSettings, fullWidthParams(4));

        Button keyboardPicker = new Button(this);
        keyboardPicker.setText("2. ВЫБРАТЬ NRV AI KEYBOARD");
        keyboardPicker.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        root.addView(keyboardPicker, fullWidthParams(8));

        Button overlayTest = new Button(this);
        overlayTest.setText("ПРОВЕРИТЬ ПЛАВАЮЩУЮ ПАНЕЛЬ");
        overlayTest.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                pendingCaptureAfterOverlay = false;
                openOverlaySettings();
            } else {
                showPermissionPreview(false);
            }
        });
        root.addView(overlayTest, fullWidthParams(8));

        TextView modeTitle = new TextView(this);
        modeTitle.setText("Режим анализа:");
        modeTitle.setTextSize(17);
        modeTitle.setPadding(0, dp(16), 0, dp(6));
        root.addView(modeTitle, fullWidthParams(0));

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        Button ai = modeButton("AI", CompanionModeStore.Mode.GENERAL);
        Button pokerMode = modeButton("POKER", CompanionModeStore.Mode.POKER);
        Button moba = modeButton("MOBA", CompanionModeStore.Mode.MOBA);
        modes.addView(ai, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        modes.addView(pokerMode, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        modes.addView(moba, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(modes, fullWidthParams(0));

        Button poker = new Button(this);
        poker.setText("POKER LAB · ТРЕНИРОВОЧНЫЙ EQUITY");
        poker.setOnClickListener(v -> startActivity(new Intent(this, PokerLabActivity.class)));
        root.addView(poker, fullWidthParams(12));

        Button capture = new Button(this);
        capture.setText("ЗАПУСТИТЬ АНАЛИЗ + ПАНЕЛЬ ПОВЕРХ ИГРЫ");
        capture.setOnClickListener(v -> startCapture());
        root.addView(capture, fullWidthParams(8));

        Button stop = new Button(this);
        stop.setText("ОСТАНОВИТЬ АНАЛИЗ ЭКРАНА");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class));
            ScreenInsightStore.publish("Захват экрана остановлен");
            refreshStatus();
        });
        root.addView(stop, fullWidthParams(8));

        statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setPadding(dp(8), dp(18), dp(8), dp(18));
        root.addView(statusView, fullWidthParams(0));

        refreshStatus();
        return scroll;
    }

    private Button modeButton(String title, CompanionModeStore.Mode mode) {
        Button button = new Button(this);
        button.setText(title);
        button.setOnClickListener(v -> {
            CompanionModeStore.set(this, mode);
            setStatus("Выбран режим: " + CompanionModeStore.title(mode));
        });
        return button;
    }

    private void refreshStatus() {
        if (statusView == null) return;
        ScreenInsightStore.Snapshot snapshot = ScreenInsightStore.read();
        String mode = CompanionModeStore.title(CompanionModeStore.get(this));
        String insight = snapshot.fresh(30_000) ? snapshot.text() : "Нет свежего анализа";
        String overlay = Settings.canDrawOverlays(this) ? "ПАНЕЛЬ: РАЗРЕШЕНА ✓" : "ПАНЕЛЬ: НЕТ РАЗРЕШЕНИЯ ✕";
        statusView.setText(overlay + "\nРежим: " + mode + "\n" + insight);
    }

    private LinearLayout.LayoutParams fullWidthParams(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private int[] getScreenSize() {
        if (Build.VERSION.SDK_INT >= 30) {
            Rect bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
            return new int[]{bounds.width(), bounds.height()};
        }
        DisplayMetrics metrics = new DisplayMetrics();
        //noinspection deprecation
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        return new int[]{metrics.widthPixels, metrics.heightPixels};
    }

    private void startCapture() {
        if (projectionManager == null) {
            Toast.makeText(this, "MediaProjection недоступен", Toast.LENGTH_LONG).show();
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            pendingCaptureAfterOverlay = true;
            setStatus("Нужно включить разрешение «Поверх других приложений» для NRV.");
            openOverlaySettings();
            return;
        }

        showPermissionPreview(true);
    }

    private void openOverlaySettings() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        overlayLauncher.launch(intent);
    }

    private void resumeAfterOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            if (pendingCaptureAfterOverlay) {
                pendingCaptureAfterOverlay = false;
                setStatus("Разрешение панели не включено. Включи «Поверх других приложений» для NRV.");
            }
            refreshStatus();
            return;
        }

        if (pendingCaptureAfterOverlay) {
            pendingCaptureAfterOverlay = false;
            showPermissionPreview(true);
        } else {
            showPermissionPreview(false);
        }
    }

    private void showPermissionPreview(boolean continueToCapture) {
        removePermissionPreview();
        if (!Settings.canDrawOverlays(this)) {
            if (continueToCapture) startCapture();
            return;
        }

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        TextView preview = new TextView(this);
        preview.setText("NRV · ПАНЕЛЬ РАБОТАЕТ ✓");
        preview.setTextColor(Color.WHITE);
        preview.setTextSize(15);
        preview.setTypeface(Typeface.DEFAULT_BOLD);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(14), dp(10), dp(14), dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(24, 92, 50));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(2), Color.rgb(121, 232, 139));
        preview.setBackground(bg);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(70);

        try {
            wm.addView(preview, params);
            permissionPreview = preview;
            setStatus("Тестовая панель показана. Если ты видишь зелёную надпись NRV — разрешение работает.");
            mainHandler.postDelayed(() -> {
                removePermissionPreview();
                if (continueToCapture && !isFinishing() && !isDestroyed()) {
                    launchCaptureConsent();
                }
            }, 1800L);
        } catch (Exception error) {
            permissionPreview = null;
            setStatus("Android не дал показать панель: " + error.getClass().getSimpleName());
            Toast.makeText(this, "Панель не запускается. Проверь разрешение поверх приложений.", Toast.LENGTH_LONG).show();
        }
    }

    private void removePermissionPreview() {
        if (permissionPreview == null) return;
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            wm.removeView(permissionPreview);
        } catch (Exception ignored) {}
        permissionPreview = null;
    }

    private void launchCaptureConsent() {
        captureLauncher.launch(projectionManager.createScreenCaptureIntent());
    }

    private void setStatus(String text) {
        if (statusView != null) statusView.setText(text);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
