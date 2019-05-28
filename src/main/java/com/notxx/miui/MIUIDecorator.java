package com.notxx.miui;

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

    @Override
    protected void apply(MutableStatusBarNotification evolving) {
        final MutableNotification n = evolving.getNotification();
        Log.d(TAG, "begin modifying");
        Icon icon = Icon.createWithResource(this, R.drawable.default_notification_icon);
        Bundle extras = n.extras;
        if ("com.xiaomi.smarthome".equals(extras.getString("target_package", null))) {
            extras.putBoolean("miui.isGrayscaleIcon", true);
            n.setSmallIcon(Icon.createWithResource(this, R.drawable.com_xiaomi_smarthome));
            n.color = Color.parseColor("#19ca89");
        } else {
            // TODO debug only
            extras.putBoolean("miui.isGrayscaleIcon", true);
            n.setSmallIcon(icon);
            n.color = Color.RED;
        }
        Log.d(TAG, "end modifying");
    }
}
