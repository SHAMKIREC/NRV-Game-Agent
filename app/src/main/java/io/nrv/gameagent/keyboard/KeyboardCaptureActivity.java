package io.nrv.gameagent.keyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import io.nrv.gameagent.capture.CaptureService;

/**
 * Permission bridge opened from the system keyboard.
 * It requests Android's standard overlay permission (once) and MediaProjection consent.
 */
public final class KeyboardCaptureActivity extends AppCompatActivity {
    private MediaProjectionManager projectionManager;
    private boolean captureRequested = false;

    private final ActivityResultLauncher<Intent> captureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    ScreenInsightStore.publish("Захват экрана не разрешён");
                    finish();
                    return;
                }

                int[] size = screenSize();
                Intent service = new Intent(this, CaptureService.class);
                service.putExtra(CaptureService.EXTRA_RESULT_CODE, result.getResultCode());
                service.putExtra(CaptureService.EXTRA_RESULT_DATA, result.getData());
                service.putExtra(CaptureService.EXTRA_WIDTH, size[0]);
                service.putExtra(CaptureService.EXTRA_HEIGHT, size[1]);
                service.putExtra(CaptureService.EXTRA_DENSITY, getResources().getDisplayMetrics().densityDpi);
                ContextCompat.startForegroundService(this, service);
                ScreenInsightStore.publish("Захват запущен · режим " + CompanionModeStore.title(CompanionModeStore.get(this)));
                finish();
            });

    private final ActivityResultLauncher<Intent> overlayLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> requestCaptureIfReady());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager == null) {
            ScreenInsightStore.publish("MediaProjection недоступен на устройстве");
            finish();
            return;
        }
        requestOverlayThenCapture();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (projectionManager != null && !captureRequested && Settings.canDrawOverlays(this)) {
            requestCaptureIfReady();
        }
    }

    private void requestOverlayThenCapture() {
        if (Settings.canDrawOverlays(this)) {
            requestCaptureIfReady();
            return;
        }

        ScreenInsightStore.publish("Разреши «Поверх других приложений», чтобы видеть NRV в игре");
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        overlayLauncher.launch(intent);
    }

    private void requestCaptureIfReady() {
        if (captureRequested) return;
        if (!Settings.canDrawOverlays(this)) {
            ScreenInsightStore.publish("Без разрешения «Поверх других приложений» панель в игре не появится");
            finish();
            return;
        }
        captureRequested = true;
        captureLauncher.launch(projectionManager.createScreenCaptureIntent());
    }

    private int[] screenSize() {
        if (Build.VERSION.SDK_INT >= 30) {
            Rect bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
            return new int[]{bounds.width(), bounds.height()};
        }
        DisplayMetrics metrics = new DisplayMetrics();
        //noinspection deprecation
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        return new int[]{metrics.widthPixels, metrics.heightPixels};
    }
}
