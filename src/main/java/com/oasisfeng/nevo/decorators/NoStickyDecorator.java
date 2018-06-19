package com.oasisfeng.nevo.decorators;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RequiresApi;
import android.util.ArraySet;
import android.util.Log;
import android.widget.Toast;

import com.oasisfeng.nevo.sdk.MutableStatusBarNotification;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import java.util.Objects;

import static android.app.Notification.EXTRA_TITLE;
import static android.app.NotificationManager.IMPORTANCE_HIGH;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.os.Build.VERSION_CODES.O;

/**
 * Created by Oasis on 2018/4/30.
 */
@RequiresApi(O) public class NoStickyDecorator extends NevoDecoratorService {

	private static final long NOTIFICATION_TIMEOUT = 60_000;
	private static final String NOTIFICATION_CHANNEL = "no_sticky";
	private static final String NOTIFICATION_CHANNEL_GROUP = "plugins";		// Pre-created by engine (only for built-in decorators)
	private static final String ACTION_HIDE = "HIDE";
	private static final String SCHEME = "key";

	@Override protected void apply(final MutableStatusBarNotification evolving) {
		if (! evolving.isOngoing()) return;
		final String key = evolving.getKey();
		if (mHiddenKeys.contains(key)) {
			snoozeNotification(key, 7 * 24 * 3600_000L);		// Never set a too-large duration to avoid time overflow.
			Log.i(TAG, "Hide blacklisted notification: " + evolving.getKey() + " (" + evolving.getNotification().extras.getCharSequence(EXTRA_TITLE) + ")");
		}
	}

	@Override protected void onNotificationRemoved(final StatusBarNotification sbn, final int reason) {
		if (reason != NotificationListenerService.REASON_SNOOZED) return;
		if (! sbn.isOngoing()) return;
		if (mHiddenKeys.contains(sbn.getKey())) return;

		final NotificationManager nm = Objects.requireNonNull(getSystemService(NotificationManager.class));
		final Intent intent = new Intent(ACTION_HIDE, Uri.fromParts(SCHEME, sbn.getKey(), null)).setPackage(getPackageName());
		nm.notify(TAG, 0, new Notification.Builder(this, ensureNotificationChannelCreated(nm)).setSmallIcon(R.drawable.ic_visibility_off_white_24dp)
				.setContentTitle(getString(R.string.no_sticky_prompt_title)).setContentText(sbn.getNotification().extras.getCharSequence(EXTRA_TITLE))
				.setContentIntent(PendingIntent.getBroadcast(this, 0, intent, FLAG_UPDATE_CURRENT)).setAutoCancel(true)
				.setSubText(getString(R.string.no_sticky_prompt_sub_text)).setTimeoutAfter(NOTIFICATION_TIMEOUT).build());
	}

	private String ensureNotificationChannelCreated(final NotificationManager nm) {
		if (! mChannelCreated) {
			final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL, getString(R.string.decorator_no_sticky_title), IMPORTANCE_HIGH);
			channel.setSound(null, null);
			channel.setShowBadge(false);
			if (nm.getNotificationChannelGroups().stream().anyMatch(group -> NOTIFICATION_CHANNEL_GROUP.equals(group.getId())))
				channel.setGroup(NOTIFICATION_CHANNEL_GROUP);		// Don't use channel group if not existent.
			nm.createNotificationChannel(channel);
			mChannelCreated = true;
		}
		return NOTIFICATION_CHANNEL;
	}

	@Override public void onCreate() {
		super.onCreate();
		final IntentFilter filter = new IntentFilter(ACTION_HIDE);
		filter.addDataScheme(SCHEME);
		registerReceiver(mHideActionReceiver, filter);	// No sender verification implemented, since we could only affect apps explicitly allowed by user.
	}

	@Override public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mHideActionReceiver);
	}

	private final BroadcastReceiver mHideActionReceiver = new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent intent) {
		final Uri data = intent.getData();
		if (data == null) return;
		final String key = data.getSchemeSpecificPart();
		if (key == null || key.isEmpty()) return;
		mHiddenKeys.add(key);
		Toast.makeText(context, R.string.no_sticky_toast, Toast.LENGTH_LONG).show();
		Log.i(TAG, "Blacklist until next boot: " + key);
	}};

	private boolean mChannelCreated;
	private final ArraySet<String> mHiddenKeys = new ArraySet<>();
}
