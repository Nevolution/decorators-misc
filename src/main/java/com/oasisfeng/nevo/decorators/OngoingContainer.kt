package com.oasisfeng.nevo.decorators

import android.app.Notification
import android.app.Notification.*
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager.*
import android.os.*
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.P
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.annotation.RequiresApi
import android.text.SpannableStringBuilder
import android.util.ArrayMap
import android.util.ArraySet
import android.util.Log
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification
import com.oasisfeng.nevo.sdk.NevoDecoratorService
import kotlin.random.Random

/**
 * Mirror snoozed ongoing (sticky) notification in a group.
 * Perform auto-snooze for ongoing notification with importance lower than default.
 *
 * Created by Oasis on 2019-8-29.
 */
private const val MAX_REPOST_WINDOW = 5_000L            // Max time window to watch notification repost after snooze expiry (determined as canceled after that)
private const val MAX_SNOOZE_DURATION = 30 * 60_000L    // Max snooze duration for long-standing notification

@RequiresApi(O) class OngoingContainer: NevoDecoratorService() {

    private val mAutoSnoozeDurationBase: Long = if (BuildConfig.DEBUG) 15_000 else 60_000

    override fun apply(sbn: MutableStatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return false
        val key = sbn.key; val n = sbn.notification
        if (n.extras.getBoolean(EXTRA_SNOOZED)) return false.also { if (sbn.isOngoing) mirror(sbn) }
        if (! sbn.isOngoing) return false.also { if (mMirroredKeys.remove(key)) cancelMirror(key) }

        val channel = n.channelId?.let { getNotificationChannel(sbn.packageName, sbn.user, it) }
        @Suppress("DEPRECATION") val importance = channel?.importance ?: n.priority + 3
        if (importance > NotificationManager.IMPORTANCE_LOW) return false

        var duration = mAutoSnoozeDurationBase + Random.nextInt(1, if (BuildConfig.DEBUG) 5_000 else 10_000)
        val last = mTime[key]   // As repost after snooze resets the postTime, we track the last postTime and reset it after snooze expires without repost (thus already canceled)
        val now = System.currentTimeMillis()
        val postTime = if (last != null && last.postTime <= sbn.postTime && now - last.snoozeExpiry <= MAX_REPOST_WINDOW)
            last.postTime else sbn.postTime
        if (duration < MAX_SNOOZE_DURATION) (now - postTime).also { if (it > 0) duration += it }   // Snooze longer for those stayed longer
        if (duration > MAX_SNOOZE_DURATION) duration = MAX_SNOOZE_DURATION
        mTime[key] = SnoozeTime(postTime, now + duration)

        snoozeNotification(key, duration)

        Log.i(TAG, "Auto snooze (${duration / 1000}s): $key")
        return false
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, reason: Int): Boolean {
        if (reason != NotificationListenerService.REASON_SNOOZED) return false
        if (! sbn.isOngoing) return false

        mirror(sbn)
        return false
    }

    override fun onNotificationRemoved(key: String, reason: Int) = false.also {     // Only this overload will be called when app cancels a snoozed notification
        if (reason == 0/* if canceled during snooze */|| reason == NotificationListenerService.REASON_APP_CANCEL) {
            Log.i(TAG, "CANCEL: $key")
            if (mMirroredKeys.remove(key)) cancelMirror(key) }
    }

    private fun mirror(sbn: StatusBarNotification) {
        mNotificationManager.notify(makeTagForN(sbn.key), 0, sbn.notification.also { n ->
            Notification::class.java.getDeclaredField("mGroupKey").apply { isAccessible = true }.set(n, NOTIFICATION_GROUP)
            Notification::class.java.getDeclaredField("mChannelId").apply { isAccessible = true }.set(n, NOTIFICATION_CHANNEL)
            n.flags = n.flags and FLAG_FOREGROUND_SERVICE.inv()    // Remove FLAG_FOREGROUND_SERVICE
            tweakAppearancesForMirror(sbn)
        })  // The single line representation of group children is implemented in NotificationContentView.updateSingleLineView() of SystemUI.
        Log.i(TAG, "NOTIFY: ${sbn.key}")

        if (mMirroredKeys.put(sbn)) updateSummaryNotification()
    }

    private fun updateSummaryNotification() {
        if (mMirroredKeys.size() <= 1) return mNotificationManager.cancel(TAG, 0)

        val la = getSystemService(LauncherApps::class.java)!!
        val appLabels = mMirroredKeys.getPackages().asSequence().mapNotNull { try {
            la.getApplicationInfo(it.second, MATCH_UNINSTALLED_PACKAGES, it.first)
        } catch (e: Exception) { null }?.loadLabel(packageManager) }.joinToString(" · ")
        mNotificationManager.notify(TAG, 0, Builder(this, NOTIFICATION_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_more).setGroupSummary(true).setLocalOnly(true)
                .setGroup(NOTIFICATION_GROUP).setGroupAlertBehavior(GROUP_ALERT_SUMMARY)
                .setSubText("${mMirroredKeys.size()} Ongoing: $appLabels").build())
    }

    private fun tweakAppearancesForMirror(sbn: StatusBarNotification) {
        val n = sbn.notification; val pkg = sbn.packageName; val extras = n.extras; val pm = packageManager
        val substAppName = extras.getString(EXTRA_SUBSTITUTE_APP_NAME)
        val appLabel = substAppName?.takeIf { pm.checkPermission(PERMISSION_SUBST_N_APP_NAME, pkg) == PERMISSION_GRANTED }
                ?: mAppLabelCache.getOrPut(pkg) { try { pm.getApplicationInfo(pkg, MATCH_DISABLED_COMPONENTS).loadLabel(pm) }
                catch (e: NameNotFoundException) { return }}
        val subText = extras.getCharSequence(EXTRA_SUB_TEXT)
        val amended = subText?.let { SpannableStringBuilder(appLabel).append(" · ").append(it) } ?: appLabel
        extras.putCharSequence(EXTRA_SUB_TEXT, amended)
    }

    private fun makeTagForN(key: String) = "$NOTIFICATION_TAG_PREFIX${key}"

    override fun onCreate() {
        registerReceiver(onTimeChanged, IntentFilter(Intent.ACTION_TIME_CHANGED))
        // ACTION_PACKAGE_RESTARTED is not handled by LauncherApps.Callback.
        registerReceiver(onPackageRestarted, IntentFilter(Intent.ACTION_PACKAGE_RESTARTED).apply { addDataScheme("package") })
    }

    override fun onDestroy() {
        unregisterReceiver(onPackageRestarted)
        unregisterReceiver(onTimeChanged)
    }

    override fun onConnected() {
        mNotificationManager = getSystemService(NotificationManager::class.java)!!
        NotificationChannel(NOTIFICATION_CHANNEL, getString(R.string.decorator_ongoing_container_title), NotificationManager.IMPORTANCE_MIN).apply {
            setSound(null, null)
            setShowBadge(false)
            if (isChannelGroupExistent(NOTIFICATION_CHANNEL_GROUP)) group = NOTIFICATION_CHANNEL_GROUP
        }.also { mNotificationManager.createNotificationChannel(it) }

        getSystemService(LauncherApps::class.java)?.registerCallback(object: LauncherApps.Callback() {
            override fun onPackageRemoved(pkg: String, user: UserHandle) {
                cancelAllForPackage(user, pkg)
                mAppLabelCache.remove(pkg)
            }
            override fun onPackagesUnavailable(pkgs: Array<out String>, user: UserHandle, replacing: Boolean)
                    = pkgs.forEach { pkg -> onPackageRemoved(pkg, user) }

            override fun onPackageAdded(packageName: String, user: UserHandle) {}
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {}
            override fun onPackageChanged(packageName: String, user: UserHandle) {}
        }, Handler(Looper.getMainLooper()))
    }

    private fun cancelAllForPackage(user: UserHandle, pkg: String) = mNotificationManager.activeNotifications.forEach { sbn ->
        if (sbn.notification.channelId == NOTIFICATION_CHANNEL
                && sbn.tag?.startsWith("$NOTIFICATION_TAG_PREFIX${user.hashCode()}|$pkg|") == true) {  // The encoding defined in makeTagForKey()
            Log.i(TAG, "CANCEL for ${pkg}: ${sbn.key}")
            mMirroredKeys.removeAllForPackage(user, pkg)?.also { cancelMirrors(it) }}
    }

    private fun cancelMirror(key: String) {
        mNotificationManager.cancel(makeTagForN(key), 0)
        updateSummaryNotification()
    }

    private fun cancelMirrors(keys: Set<String>) {
        keys.forEach { mNotificationManager.cancel(makeTagForN(it), 0) }
        updateSummaryNotification()
    }

    @Suppress("SameParameterValue") private fun isChannelGroupExistent(group: String): Boolean {
        return if (SDK_INT >= P) mNotificationManager.getNotificationChannelGroup(group) != null
        else mNotificationManager.notificationChannelGroups.any { it.id == group }
    }

    private val onPackageRestarted = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.also { pkg -> cancelAllForPackage(Process.myUserHandle(), pkg) }
        }
    }

    private val onTimeChanged = object: BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) {
        mTime.clear() }}

    private lateinit var mNotificationManager: NotificationManager
    private val mMirroredKeys = NotificationKeys()
    private val mAppLabelCache = ArrayMap<String, CharSequence>()
    private val mTime = ArrayMap<String/* key */, SnoozeTime>()

    private class NotificationKeys {

        fun getPackages() = mKeysByUserPackage.keys.map { k -> k.split('|').let { Pair(it[0].toInt().toUserHandle(), it[1]) }}
        fun size() = mKeysByUserPackage.values.sumBy { it.size }

        fun put(sbn: StatusBarNotification)
                = mKeysByUserPackage.getOrPut(keyOf(sbn.user, sbn.packageName)) { ArraySet<String>() }.add(sbn.key)

        fun remove(key: String): Boolean {
            val userPackage = key.substring(0, key.indexOf('|', key.indexOf('|') + 1))  // The first two parts: <user>|<package>
            val keysInPackage = mKeysByUserPackage[userPackage] ?: return false
            return keysInPackage.remove(key).also {
                if (it && keysInPackage.isEmpty()) mKeysByUserPackage.remove(userPackage) }
        }

        fun removeAllForPackage(user: UserHandle, pkg: String): Set<String>? = mKeysByUserPackage.remove(keyOf(user, pkg))

        private fun keyOf(user: UserHandle, pkg: String) = "${user.hashCode()}|$pkg"

        private fun Int.toUserHandle(): UserHandle {
            if (this == mCurrentUserId) return mCurrentUser
            val parcel = Parcel.obtain()
            return try {
                val begin = parcel.dataPosition()
                parcel.writeInt(this)
                parcel.setDataPosition(begin)
                UserHandle.CREATOR.createFromParcel(parcel) }
            finally { parcel.recycle() }
        }

        private val mKeysByUserPackage: MutableMap<String/* <userId>|<packageName> */, MutableSet<String>>
                = LinkedHashMap(4, 0.75f, true)
        private val mCurrentUser = Process.myUserHandle()
        private val mCurrentUserId = mCurrentUser.hashCode()
    }
}

data class SnoozeTime(val postTime: Long, val snoozeExpiry: Long)

private const val NOTIFICATION_TAG_PREFIX = "nevo.snoozed:"
private const val NOTIFICATION_GROUP = "nevo.snoozed"
private const val NOTIFICATION_CHANNEL = "snoozed"
private const val NOTIFICATION_CHANNEL_GROUP = "plugins"            // Pre-created by engine (only for built-in decorators)
private const val EXTRA_SUBSTITUTE_APP_NAME = "android.substName"   // Notification.EXTRA_SUBSTITUTE_APP_NAME in String
private const val PERMISSION_SUBST_N_APP_NAME = "android.permission.SUBSTITUTE_NOTIFICATION_APP_NAME"   // signature | privileged

private const val EXTRA_SNOOZED = "nevo.snoozed"    // To be added to NevoDecoratorService in upcoming version of SDK.
