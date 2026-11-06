// Reads notifications from the owner's chosen finance apps only; others are dropped before any content is read.
package id.glideslope.app

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import id.glideslope.core.Raw
import id.glideslope.core.capture

class Listener : NotificationListenerService() {
    override fun onListenerConnected() {
        // catch up on what is still showing; anything dismissed while disconnected is gone, the dedupe key prevents doubles
        runCatching { activeNotifications }.getOrNull()?.forEach(::take)
    }

    override fun onListenerDisconnected() = requestRebind(ComponentName(this, Listener::class.java))

    override fun onNotificationPosted(sbn: StatusBarNotification) = take(sbn)

    private fun take(sbn: StatusBarNotification) {
        val allowed = Settings(this).allowed
        if (sbn.packageName !in allowed) return
        val n = sbn.notification
        val e = n.extras
        val raw = Raw(
            pkg = sbn.packageName,
            key = sbn.key,
            postTime = sbn.postTime,
            title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            bigText = e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty(),
            text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            ongoing = sbn.isOngoing,
        )
        val p = capture(raw, allowed) ?: return
        // shortcut: one small insert on the main thread; move to a background thread if notifications ever burst
        if (Queue.get(this).add(p)) SendWorker.enqueue(this)
    }
}
