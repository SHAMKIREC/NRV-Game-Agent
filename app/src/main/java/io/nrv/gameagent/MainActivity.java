package io.nrv.gameagent;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
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
import io.nrv.gameagent.keyboard.CompanionModeStore;
import io.nrv.gameagent.keyboard.ScreenInsightStore;
import io.nrv.gameagent.poker.PokerLabActivity;

public class MainActivity extends AppCompatActivity {

    private MediaProjectionManager projectionManager;
    private TextView statusView;

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
                setStatus("Захват активен · режим " + CompanionModeStore.title(CompanionModeStore.get(this)));
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
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
        subtitle.setText("Клавиатура + разрешённый анализ экрана · без Accessibility");
        subtitle.setTextSize(16);
        subtitle.setPadding(0, dp(6), 0, dp(16));
        root.addView(subtitle);

        TextView setup = new TextView(this);
        setup.setText("1) Включи NRV AI Keyboard\n2) Выбери её текущей клавиатурой\n3) В клавиатуре выбери POKER / MOBA / AI и нажми ЭКРАН");
        setup.setTextSize(16);
        setup.setPadding(dp(8), dp(8), dp(8), dp(14));
        root.addView(setup, fullWidthParams(0));

        Button keyboardSettings = new Button(this);
        keyboardSettings.setText("1. ВКЛЮЧИТЬ NRV AI KEYBOARD");
        keyboardSettings.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            setStatus("Включи NRV AI Keyboard в списке клавиатур Android");
        });
        root.addView(keyboardSettings, fullWidthParams(4));

        Button keyboardPicker = new Button(this);
        keyboardPicker.setText("2. ВЫБРАТЬ NRV AI KEYBOARD");
        keyboardPicker.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        root.addView(keyboardPicker, fullWidthParams(8));

        Button poker = new Button(this);
        poker.setText("POKER LAB · ТРЕНИРОВОЧНЫЙ EQUITY");
        poker.setOnClickListener(v -> startActivity(new Intent(this, PokerLabActivity.class)));
        root.addView(poker, fullWidthParams(12));

        Button capture = new Button(this);
        capture.setText("ЗАПУСТИТЬ АНАЛИЗ ЭКРАНА ИЗ ПРИЛОЖЕНИЯ");
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

    private void refreshStatus() {
        if (statusView == null) return;
        ScreenInsightStore.Snapshot snapshot = ScreenInsightStore.read();
        String mode = CompanionModeStore.title(CompanionModeStore.get(this));
        String insight = snapshot.fresh(30_000) ? snapshot.text() : "Нет свежего анализа";
        statusView.setText("Режим: " + mode + "\n" + insight);
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
        captureLauncher.launch(projectionManager.createScreenCaptureIntent());
    }

    private void setStatus(String text) {
        if (statusView != null) statusView.setText(text);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
