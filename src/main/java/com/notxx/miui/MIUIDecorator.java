package com.notxx.miui;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.util.Log;

import com.oasisfeng.nevo.sdk.MutableNotification;
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;

public class MIUIDecorator extends NevoDecoratorService {
    private static final String CHANNEL = "test_channel";

    @Override
    protected void apply(MutableStatusBarNotification evolving) {
        final MutableNotification n = evolving.getNotification();
        Log.d(TAG, "begin modifying");
        Icon icon = Icon.createWithResource(this, R.drawable.default_notification_icon);
        n.setSmallIcon(icon);
        n.setLargeIcon(icon);
        n.color = Color.RED;
//        n.extras.putCharSequence(Notification.EXTRA_TITLE, "title replaced");
        Log.d(TAG, "end modifying");
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL);
        if (channel == null) {
            channel = new NotificationChannel(CHANNEL, "test channel", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("channel for testing");
            notificationManager.createNotificationChannel(channel);
        }
        Notification notif = new Notification.Builder(getApplicationContext(), CHANNEL)
                .setContentTitle(n.extras.getCharSequence(Notification.EXTRA_TITLE))
                .setContentText(n.extras.getCharSequence(Notification.EXTRA_TEXT))
                .setSmallIcon(icon)
                .setLargeIcon(icon)
                .setColor(Color.RED)
                .build();
        notificationManager.notify(1, notif);
        Log.d(TAG, "do notify");
    }
}
