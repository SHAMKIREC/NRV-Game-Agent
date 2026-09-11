package io.nrv.gameagent;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import io.nrv.gameagent.capture.CaptureService;

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
                setStatus("Захват активен. Агент получает кадры.");
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        requestNotificationPermissionIfNeeded();
        setContentView(buildContent());
    }

    private LinearLayout buildContent() {
        int padding = dp(24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView title = new TextView(this);
        title.setText("NRV GAME AGENT");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Этап 1 · зрение и телеметрия");
        subtitle.setTextSize(16);
        subtitle.setPadding(0, dp(8), 0, dp(32));
        root.addView(subtitle);

        statusView = new TextView(this);
        statusView.setText("Готов к запуску");
        statusView.setTextSize(18);
        statusView.setPadding(0, 0, 0, dp(24));
        root.addView(statusView);

        Button start = new Button(this);
        start.setText("НАЧАТЬ ЗАХВАТ ЭКРАНА");
        start.setOnClickListener(v -> startCapture());
        root.addView(start, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button stop = new Button(this);
        stop.setText("ОСТАНОВИТЬ");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class));
            setStatus("Захват остановлен");
        });
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        stopParams.topMargin = dp(12);
        root.addView(stop, stopParams);

        return root;
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
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void setStatus(String text) {
        if (statusView != null) statusView.setText(text);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
