package io.nrv.gameagent.capture;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
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

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import io.nrv.gameagent.agent.Decision;
import io.nrv.gameagent.agent.GameState;
import io.nrv.gameagent.agent.RuleAgent;
import io.nrv.gameagent.keyboard.CompanionModeStore;
import io.nrv.gameagent.keyboard.ScreenInsightStore;
import io.nrv.gameagent.poker.PokerVisionAnalyzer;
import io.nrv.gameagent.poker.PokerVisionObservation;
import io.nrv.gameagent.vision.EnemyObservation;
import io.nrv.gameagent.vision.EnemyTracker;
import io.nrv.gameagent.vision.HudObservation;
import io.nrv.gameagent.vision.MlbbHudAnalyzer;

public class CaptureService extends Service {

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_WIDTH = "width";
    public static final String EXTRA_HEIGHT = "height";
    public static final String EXTRA_DENSITY = "density";

    private static final String TAG = "NRVCapture";
    private static final String CHANNEL_ID = "nrv_capture";
    private static final int NOTIFICATION_ID = 101;
    private static final int ANALYZE_EVERY_N_FRAMES = 8;
    private static final int POKER_SAMPLE_MAX_WIDTH = 640;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final AtomicLong frameCount = new AtomicLong(0);
    private final MlbbHudAnalyzer hudAnalyzer = new MlbbHudAnalyzer();
    private final EnemyTracker enemyTracker = new EnemyTracker();
    private final RuleAgent ruleAgent = new RuleAgent();
    private final PokerVisionAnalyzer pokerVisionAnalyzer = new PokerVisionAnalyzer();

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

        var notification = buildNotification("Получаю кадры экрана для анализа");
        ScreenInsightStore.publish("Захват экрана активирован. Ожидаю кадры…");

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
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        int width = intent.getIntExtra(EXTRA_WIDTH, 1280);
        int height = intent.getIntExtra(EXTRA_HEIGHT, 720);
        int density = intent.getIntExtra(EXTRA_DENSITY, getResources().getDisplayMetrics().densityDpi);

        if (resultData == null || resultCode != Activity.RESULT_OK) {
            Log.e(TAG, "Missing MediaProjection permission data");
            ScreenInsightStore.publish("Захват экрана не разрешён");
            stopSelf();
            return;
        }

        cleanupProjection(false);
        enemyTracker.reset();
        frameCount.set(0);

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = manager.getMediaProjection(resultCode, resultData);
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.i(TAG, "MediaProjection stopped by system/user");
                ScreenInsightStore.publish("Захват экрана остановлен");
                projection = null;
                cleanupProjection(false);
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
                if (count % ANALYZE_EVERY_N_FRAMES != 0) return;

                CompanionModeStore.Mode mode = CompanionModeStore.get(this);
                String status = switch (mode) {
                    case POKER -> analyzePoker(image);
                    case MOBA -> analyzeMoba(image);
                    case GENERAL -> analyzeGeneral(image, count);
                };

                ScreenInsightStore.publish(status);
                updateNotification(status);
            } catch (Exception error) {
                Log.e(TAG, "Frame processing failed", error);
                ScreenInsightStore.publish("Ошибка анализа кадра: " + error.getClass().getSimpleName());
            } finally {
                if (image != null) image.close();
            }
        }, null);

        virtualDisplay = projection.createVirtualDisplay(
                "NRV-AI-Keyboard-Capture",
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

    private String analyzePoker(Image image) {
        Bitmap bitmap = imageToSampledBitmap(image, POKER_SAMPLE_MAX_WIDTH);
        if (bitmap == null) return "POKER · кадр не прочитан";
        try {
            PokerVisionObservation observation = pokerVisionAnalyzer.analyze(bitmap);
            return String.format(
                    Locale.US,
                    "POKER · карты:%d · мои:%d · стол:%d · %s · conf %.0f%%",
                    observation.cardCandidates(),
                    observation.likelyHeroCards(),
                    observation.likelyBoardCards(),
                    observation.stage(),
                    observation.confidence() * 100.0
            );
        } finally {
            bitmap.recycle();
        }
    }

    private String analyzeMoba(Image image) {
        HudObservation rawObservation = hudAnalyzer.analyze(image);
        EnemyObservation trackedEnemy = enemyTracker.update(rawObservation.enemies());
        HudObservation observation = new HudObservation(
                rawObservation.hpRatio(),
                rawObservation.manaRatio(),
                rawObservation.hpConfidence(),
                rawObservation.manaConfidence(),
                rawObservation.landscape(),
                rawObservation.dead(),
                trackedEnemy
        );

        GameState gameState = observation.toGameState();
        Decision decision = ruleAgent.decide(gameState);
        String enemyStatus = trackedEnemy.detected()
                ? trackedEnemy.direction().name() + " " + String.format(Locale.US, "%.2f", trackedEnemy.distance())
                : "NONE";

        return String.format(
                Locale.US,
                "MOBA · HP %.0f%% · E:%s · %s",
                observation.hpRatio() * 100.0,
                enemyStatus,
                decision.name()
        );
    }

    private String analyzeGeneral(Image image, long count) {
        String orientation = image.getWidth() >= image.getHeight() ? "landscape" : "portrait";
        return String.format(
                Locale.US,
                "AI · %s · экран %dx%d · кадр %,d",
                orientation,
                image.getWidth(),
                image.getHeight(),
                count
        );
    }

    private Bitmap imageToSampledBitmap(Image image, int maxWidth) {
        if (image == null || image.getPlanes().length == 0) return null;
        Image.Plane plane = image.getPlanes()[0];
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        if (pixelStride < 4 || rowStride <= 0) return null;

        int srcWidth = image.getWidth();
        int srcHeight = image.getHeight();
        int outWidth = Math.min(srcWidth, maxWidth);
        int outHeight = Math.max(1, Math.round(srcHeight * (outWidth / (float) srcWidth)));
        int[] pixels = new int[outWidth * outHeight];
        ByteBuffer buffer = plane.getBuffer().duplicate();
        int limit = buffer.limit();

        for (int y = 0; y < outHeight; y++) {
            int srcY = Math.min(srcHeight - 1, (int) ((long) y * srcHeight / outHeight));
            int rowBase = srcY * rowStride;
            for (int x = 0; x < outWidth; x++) {
                int srcX = Math.min(srcWidth - 1, (int) ((long) x * srcWidth / outWidth));
                int offset = rowBase + srcX * pixelStride;
                if (offset + 3 >= limit) continue;
                int r = buffer.get(offset) & 0xff;
                int g = buffer.get(offset + 1) & 0xff;
                int b = buffer.get(offset + 2) & 0xff;
                int a = buffer.get(offset + 3) & 0xff;
                pixels[y * outWidth + x] = Color.argb(a, r, g, b);
            }
        }

        return Bitmap.createBitmap(pixels, outWidth, outHeight, Bitmap.Config.ARGB_8888);
    }

    private android.app.Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("NRV AI Keyboard")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "NRV screen analysis",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void cleanupProjection(boolean stopProjection) {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (stopProjection && projection != null) {
            MediaProjection current = projection;
            projection = null;
            current.stop();
        }
    }

    @Override
    public void onDestroy() {
        enemyTracker.reset();
        ScreenInsightStore.publish("Захват экрана остановлен");
        cleanupProjection(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
