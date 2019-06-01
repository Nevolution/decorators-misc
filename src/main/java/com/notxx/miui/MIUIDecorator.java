package com.notxx.miui;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.util.ArrayMap;
import android.util.Log;

import com.oasisfeng.nevo.sdk.MutableNotification;
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import top.trumeet.common.cache.IconCache;

public class MIUIDecorator extends NevoDecoratorService {

    private static final String NOTIFICATION_SMALL_ICON = "mipush_small_notification";

    private ArrayMap<String, String> embed;

    @Override
    protected void onConnected() {
        // Log.d(TAG, "begin onConnected");
        String[] array = getResources().getStringArray(R.array.decorator_miui_embed);
        this.embed = new ArrayMap<>(array.length);
        // Log.d(TAG, "array: " + array.length);
        for (String s : array) {
            this.embed.put(s, s.toLowerCase().replaceAll("\\.", "_"));
            // Log.d(TAG, s);
        }
        // Log.d(TAG, "end onConnected");
    }

    @Override
    protected void apply(MutableStatusBarNotification evolving) {
        final MutableNotification n = evolving.getNotification();
        Log.d(TAG, "begin modifying");
        Icon defIcon = Icon.createWithResource(this, R.drawable.default_notification_icon);
        Bundle extras = n.extras;
        String packageName = null;
        try {
            packageName = evolving.getPackageName();
            if ("com.xiaomi.xmsf".equals(packageName))
                packageName = extras.getString("target_package", null);
		} catch (final RuntimeException ignored) {}    // Fall-through
        if (packageName == null) {
            Log.e(TAG, "packageName is null");
            return;
        }
        extras.putBoolean("miui.isGrayscaleIcon", true);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // do nothing
        } else {
            int iconId;
            // Log.d(TAG, "packageName: " + packageName);
            if (embed.containsKey(packageName)) {
                String key = embed.get(packageName);
                // Log.d(TAG, "key: " + key);
                iconId = getResources().getIdentifier(key, "drawable", getPackageName());
                // Log.d(TAG, "iconId: " + iconId);
                // Log.d(TAG, "com.xiaomi.smarthome iconId: " + R.drawable.com_xiaomi_smarthome);
                if (iconId > 0) // has icon
                    n.setSmallIcon(Icon.createWithResource(this, iconId));
                int colorId = getResources().getIdentifier(key, "string", getPackageName());
                // Log.d(TAG, "colorId: " + colorId);
                if (colorId > 0) // has color
                    n.color = Color.parseColor(getString(colorId));
            } else {
                iconId = getResources().getIdentifier(NOTIFICATION_SMALL_ICON, "drawable", packageName);
                if (iconId > 0) // has icon
                    n.setSmallIcon(Icon.createWithResource(packageName, iconId));
            }
            if (iconId <= 0) { // does not have icon
                Icon iconCache = IconCache.getInstance().getIconCache(this, packageName, (ctx, b) -> Icon.createWithBitmap(b));
                if (iconCache != null) {
                    n.setSmallIcon(iconCache);
                } else {
                    n.setSmallIcon(defIcon);
                }
            }
        }
        Log.d(TAG, "end modifying");
    }
}
