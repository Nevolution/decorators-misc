package com.notxx.miui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.oasisfeng.nevo.sdk.MutableNotification;
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import top.trumeet.common.cache.IconCache;

import java.util.Set;

public class MIUIDecorator extends NevoDecoratorService {

    private static final String NOTIFICATION_SMALL_ICON = "mipush_small_notification";

    private static class IconSet {
        final Icon icon;
        final Color color;

        IconSet(Context context, int icon, Color color) {
            this.icon = Icon.createWithResource(context, icon);
            this.color = color;
        }
    }
    @Override
    protected void apply(MutableStatusBarNotification evolving) {
        final MutableNotification n = evolving.getNotification();
        Log.d(TAG, "begin modifying");
        Icon defIcon = Icon.createWithResource(this, R.drawable.default_notification_icon);
        Bundle extras = n.extras;
        String packageName = extras.getString("target_package", null);
        if ("com.xiaomi.smarthome".equals(packageName)) {
            extras.putBoolean("miui.isGrayscaleIcon", true);
            n.setSmallIcon(Icon.createWithResource(this, R.drawable.com_xiaomi_smarthome));
            n.color = Color.parseColor("#19ca89");
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int iconSmallId = getIconId(this, packageName, NOTIFICATION_SMALL_ICON);
            if (iconSmallId <= 0) {
                Icon iconCache = IconCache.getInstance().getIconCache(this, packageName, (ctx, b) -> Icon.createWithBitmap(b));
                if (iconCache != null) {
                    n.setSmallIcon(iconCache);
                } else {
                    n.setSmallIcon(defIcon);
                }
            } else {
                n.setSmallIcon(Icon.createWithResource(packageName, iconSmallId));
            }
        } else {
            // TODO debug only
            extras.putBoolean("miui.isGrayscaleIcon", true);
            n.setSmallIcon(defIcon);
            n.color = Color.RED;
        }
        Log.d(TAG, "end modifying");
    }

    private static int getIconId(Context context, String str, String str2) {
        return context.getResources().getIdentifier(str2, "drawable", str);
    }
}
