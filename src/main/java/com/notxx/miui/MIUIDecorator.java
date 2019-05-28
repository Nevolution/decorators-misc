package com.notxx.miui;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.Log;

import com.oasisfeng.nevo.sdk.MutableNotification;
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import java.util.Set;

public class MIUIDecorator extends NevoDecoratorService {
    private static final String CHANNEL = "test_channel";

    private int id = 0;

    @Override
    protected void apply(MutableStatusBarNotification evolving) {
        final MutableNotification n = evolving.getNotification();
        Log.d(TAG, "begin modifying");
        Icon icon = Icon.createWithResource(this, R.drawable.default_notification_icon);
        Bundle extras = n.extras;
        if ("com.xiaomi.smarthome".equals(extras.getString("target_package", null))) {
            extras.putBoolean("miui.isGrayscaleIcon", true);
            n.setSmallIcon(Icon.createWithResource(this, R.drawable.com_xiaomi_smarthome));
        } else {
            // TODO debug only
            extras.putBoolean("miui.isGrayscaleIcon", true);
            n.setSmallIcon(icon);
        }
        Log.d(TAG, "end modifying");

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL);
        if (channel == null) {
            channel = new NotificationChannel(CHANNEL, "test channel", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("channel for testing");
            notificationManager.createNotificationChannel(channel);
        }
        Set<String> keys = extras.keySet();
        StringBuilder builder = new StringBuilder();
        for (String key : keys) {
            builder.append(key);
            if ("android.appInfo".equals(key)
                    || "target_package".equals(key)
                    || "miui.isGrayscaleIcon".equals(key)
                    || "nevo.pkg".equals(key)) {
                Object value = extras.getString(key);
                if (value != null)
                    builder.append("=[").append(value.getClass()).append("]").append(value);
                else
                    builder.append("=null");
            }
            builder.append(';');
        }

        String contentText = builder.toString();
        Notification notif = new Notification.Builder(getApplicationContext(), CHANNEL)
                .setSmallIcon(icon)
                .setContentTitle(extras.getCharSequence(Notification.EXTRA_TITLE))
                .setStyle(new Notification.BigTextStyle().bigText(contentText))
                .setContentText(contentText)
                .build();
        notificationManager.notify(id++, notif);
    }
}
