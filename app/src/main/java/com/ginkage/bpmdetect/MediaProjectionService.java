package com.ginkage.bpmdetect;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class MediaProjectionService extends Service {

    private static final int ONGOING_NOTIFICATION_ID = 0x1111;
    private static final String NOTIFICATION_CHANNEL_ID = "BpmDetect";
    private static final String NOTIFICATION_CHANNEL_NAME = "BPM Detector Audio Capture";

    /** Interface for binding the service to an activity. */
    class LocalBinder extends Binder {
        /**
         * Get the service instance from the Binder proxy.
         *
         * @return Service instance.
         */
        MediaProjectionService getService() {
            return MediaProjectionService.this;
        }
    }

    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private CaptureThread captureThread;

    private final IBinder binder = new LocalBinder();
    private NotificationManager notificationManager;
    boolean isForeground;

    private final NotificationChannel notificationChannel =
            new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW);

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaProjectionManager = getSystemService(MediaProjectionManager.class);
        notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    @Override
    public void onDestroy() {
        stopCapture();

        if (isForeground) {
            stopForeground(true);
            isForeground = false;
        }

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification();

        if (isForeground) {
            if (notificationManager != null) {
                notificationManager.notify(ONGOING_NOTIFICATION_ID, notification);
            }
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification);
            isForeground = true;
        }

        return START_STICKY;
    }

    Intent createScreenCaptureIntent() {
        return mediaProjectionManager.createScreenCaptureIntent();
    }

    boolean getMediaProjection(int resultCode, Intent resultData) {
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData);
        return mediaProjection != null;
    }

    void startCapture(RenderThread renderThread) {
        if (mediaProjection != null && captureThread == null) {
            captureThread = new CaptureThread();
            captureThread.startCapture(mediaProjection);
        }
        if (captureThread != null) {
            renderThread.setDataSource(captureThread);
        }
    }

    void stopCapture() {
        if (captureThread != null) {
            captureThread.stopCapture();
            captureThread = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private Notification buildNotification() {
        Intent intent =
                new Intent(this, FullscreenActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .setAction(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        return new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setLocalOnly(true)
                .setOngoing(true)
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.detecting_bpm))
                .setContentIntent(pendingIntent)
                .build();
    }

}
