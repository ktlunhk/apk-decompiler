/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import com.mydecompilers.Constants;
import com.mydecompilers.data.PackageInfo;
import com.mydecompilers.managers.DecompilerTaskManager;
import com.mydecompilers.ui.DecompilerActivity;

/**
 * Foreground service that keeps the process alive and shows progress while a decompile runs.
 * The work itself is done by {@link DecompilerTaskManager}.
 */
public class DecompilerService extends Service implements DecompilerTaskManager.Listener {

    private NotificationManager nm;
    private PowerManager.WakeLock wakeLock;
    private long lastUpdate = 0L;
    private boolean finished = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannels();
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "myAPKDecompiler:task");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(60L * 60L * 1000L);
        } catch (Exception ignored) {
        }
        DecompilerTaskManager.addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && Constants.ACTION_STOP.equals(intent.getAction())) {
            DecompilerTaskManager.stop();
        }
        startForeground(Constants.PROGRESS_NOTIFICATION_ID, buildProgressNotification());
        if (!DecompilerTaskManager.isRunning()) finishUp();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        DecompilerTaskManager.removeListener(this);
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    public void onTaskChanged() {
        if (finished) return;
        if (!DecompilerTaskManager.isRunning()) {
            finishUp();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastUpdate < 700L) return;
        lastUpdate = now;
        nm.notify(Constants.PROGRESS_NOTIFICATION_ID, buildProgressNotification());
    }

    private void finishUp() {
        if (finished) return;
        finished = true;
        DecompilerTaskManager.State s = DecompilerTaskManager.getState();
        PackageInfo info = DecompilerTaskManager.getActivePackage();
        stopForeground(true);
        nm.cancel(Constants.PROGRESS_NOTIFICATION_ID);
        if (info != null && (s == DecompilerTaskManager.State.COMPLETED || s == DecompilerTaskManager.State.FAILED)) {
            Notification.Builder b = newBuilder(Constants.COMPLETION_CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(s == DecompilerTaskManager.State.COMPLETED ? "Decompilation finished" : "Decompilation failed")
                    .setContentText(info.label)
                    .setAutoCancel(true)
                    .setContentIntent(openIntent(info));
            nm.notify(Constants.COMPLETED_NOTIFICATION_ID, b.build());
        }
        stopSelf();
    }

    private PendingIntent openIntent(PackageInfo info) {
        Intent open = new Intent(this, DecompilerActivity.class);
        open.putExtra(Constants.EXTRA_PACKAGE_INFO, info);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 1, open, pendingFlags());
    }

    private int pendingFlags() {
        return Build.VERSION.SDK_INT >= 23
                ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.FLAG_UPDATE_CURRENT;
    }

    private Notification.Builder newBuilder(String channel) {
        if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(this, channel);
        return new Notification.Builder(this);
    }

    private Notification buildProgressNotification() {
        PackageInfo info = DecompilerTaskManager.getActivePackage();
        String title = info != null ? info.label : "myAPK Decompiler";
        String text = DecompilerTaskManager.getStatusText();
        int progress = DecompilerTaskManager.getProgress();

        Intent stop = new Intent(this, DecompilerService.class);
        stop.setAction(Constants.ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop, pendingFlags());

        Notification.Builder b = newBuilder(Constants.PROGRESS_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress, progress <= 0)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi).build());
        if (info != null) b.setContentIntent(openIntent(info));
        return b.build();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel progress = new NotificationChannel(Constants.PROGRESS_CHANNEL,
                "Decompilation progress", NotificationManager.IMPORTANCE_LOW);
        NotificationChannel done = new NotificationChannel(Constants.COMPLETION_CHANNEL,
                "Decompilation finished", NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(progress);
        nm.createNotificationChannel(done);
    }
}
