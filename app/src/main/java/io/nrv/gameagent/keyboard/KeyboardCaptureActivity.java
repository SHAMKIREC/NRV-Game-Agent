package io.nrv.gameagent.keyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import io.nrv.gameagent.capture.CaptureService;

/**
 * Tiny permission bridge opened from the system keyboard.
 * Android still shows its own MediaProjection consent dialog; nothing is bypassed.
 */
public final class KeyboardCaptureActivity extends AppCompatActivity {
    private MediaProjectionManager projectionManager;

    private final ActivityResultLauncher<Intent> launcher =
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager == null) {
            ScreenInsightStore.publish("MediaProjection недоступен на устройстве");
            finish();
            return;
        }
        launcher.launch(projectionManager.createScreenCaptureIntent());
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
