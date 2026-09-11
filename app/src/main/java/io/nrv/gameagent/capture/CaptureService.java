package io.nrv.gameagent.capture;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.concurrent.atomic.AtomicLong;

public class CaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_WIDTH = "width";
    public static final String EXTRA_HEIGHT = "height";
    public static final String EXTRA_DENSITY = "density";

    private static final String TAG = "NRVCapture";
    private static final String CHANNEL_ID = "nrv_capture";
    private static final int NOTIFICATION_ID = 101;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final AtomicLong frameCount = new AtomicLong(0);

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        var notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("NRV Game Agent")
                .setContentText("Получаю кадры экрана для анализа")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        startProjection(intent);
        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void startProjection(Intent intent) {
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
        Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        int width = intent.getIntExtra(EXTRA_WIDTH, 1280);
        int height = intent.getIntExtra(EXTRA_HEIGHT, 720);
        int density = intent.getIntExtra(EXTRA_DENSITY, getResources().getDisplayMetrics().densityDpi);

        if (resultData == null || resultCode == -1) {
            Log.e(TAG, "Missing MediaProjection permission data");
            stopSelf();
            return;
        }

        cleanupProjection();

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = manager.getMediaProjection(resultCode, resultData);
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.i(TAG, "MediaProjection stopped by system/user");
                cleanupProjection();
                stopSelf();
            }
        }, null);

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) return;

                long count = frameCount.incrementAndGet();
                if (count % 30 == 0) {
                    Log.d(TAG, "frames=" + count + " size=" + image.getWidth() + "x" + image.getHeight());
                }

                // Следующий этап: передать плоскость RGBA в VisionPipeline.
                // Сейчас MVP только подтверждает стабильное получение кадров.
            } catch (Exception error) {
                Log.e(TAG, "Frame processing failed", error);
            } finally {
                if (image != null) image.close();
            }
        }, null);

        virtualDisplay = projection.createVirtualDisplay(
                "NRV-Game-Agent-Capture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                null
        );

        Log.i(TAG, "Capture started: " + width + "x" + height + " @" + density);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "NRV screen capture",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void cleanupProjection() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
    }

    @Override
    public void onDestroy() {
        cleanupProjection();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
