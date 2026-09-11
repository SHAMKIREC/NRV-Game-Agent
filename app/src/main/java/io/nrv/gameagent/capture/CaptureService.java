package io.nrv.gameagent.capture;

import android.app.Activity;
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

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import io.nrv.gameagent.agent.Decision;
import io.nrv.gameagent.agent.GameState;
import io.nrv.gameagent.agent.RuleAgent;
import io.nrv.gameagent.agent.TacticalIntent;
import io.nrv.gameagent.agent.TacticalPlanner;
import io.nrv.gameagent.vision.EnemyObservation;
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

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final AtomicLong frameCount = new AtomicLong(0);
    private final MlbbHudAnalyzer hudAnalyzer = new MlbbHudAnalyzer();
    private final RuleAgent ruleAgent = new RuleAgent();
    private final TacticalPlanner tacticalPlanner = new TacticalPlanner();

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
            stopSelf();
            return;
        }

        cleanupProjection(false);

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = manager.getMediaProjection(resultCode, resultData);
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.i(TAG, "MediaProjection stopped by system/user");
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
                if (count % ANALYZE_EVERY_N_FRAMES == 0) {
                    HudObservation observation = hudAnalyzer.analyze(image);
                    GameState gameState = observation.toGameState();
                    Decision decision = ruleAgent.decide(gameState);
                    EnemyObservation enemy = observation.enemies();
                    TacticalIntent intentPlan = tacticalPlanner.plan(decision, gameState, enemy);

                    String enemyStatus = enemy.detected()
                            ? enemy.direction().name() + " " + String.format(Locale.US, "%.2f", enemy.distance())
                            : "NONE";

                    String status = String.format(
                            Locale.US,
                            "HP %.0f%% · E:%s · %s",
                            observation.hpRatio() * 100.0,
                            enemyStatus,
                            decision.name()
                    );

                    Log.d(
                            TAG,
                            "frames=" + count +
                                    " hp=" + observation.hpRatio() +
                                    " mana=" + observation.manaRatio() +
                                    " enemies=" + enemy.count() +
                                    " enemyDir=" + enemy.direction() +
                                    " enemyDist=" + enemy.distance() +
                                    " decision=" + decision +
                                    " moveX=" + intentPlan.moveX() +
                                    " moveY=" + intentPlan.moveY() +
                                    " attack=" + intentPlan.wantsBasicAttack() +
                                    " skill=" + intentPlan.wantsSkill()
                    );
                    updateNotification(status);
                }
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

    private android.app.Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("NRV Game Agent")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
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
        cleanupProjection(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
