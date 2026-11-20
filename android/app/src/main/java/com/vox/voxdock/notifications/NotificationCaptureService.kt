package com.vox.voxdock.notifications

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationCaptureService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationStore.setConnected(true)
        NotificationStore.cancelCallback = { key ->
            runCatching { cancelNotification(key) }
        }
        refreshActive()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationStore.setConnected(false)
        NotificationStore.cancelCallback = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationStore.setConnected(true)
        toCaptured(sbn)?.let { NotificationStore.upsert(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationStore.removeByKey(sbn.key)
    }

    private fun refreshActive() {
        val active = runCatching { activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList())
        NotificationStore.replaceAll(active.mapNotNull { toCaptured(it) })
    }

    private fun toCaptured(sbn: StatusBarNotification): CapturedNotification? {
        if (sbn.packageName == packageName) return null
        if (sbn.isOngoing) return null

        val source = classifySource(sbn.packageName, sbn.notification) ?: return null
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        var text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()

        // Extract latest message from MessagingStyle / InboxStyle (used by Telegram groups & summaries)
        if (text.isBlank()) {
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (!lines.isNullOrEmpty()) {
                text = lines.last().toString().trim()
            }
        }
        if (text.isBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            val msgs = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            val lastBundle = msgs?.lastOrNull() as? android.os.Bundle
            val senderPrefix = lastBundle?.getCharSequence("sender")?.toString()?.trim().orEmpty()
            val msgBody = lastBundle?.getCharSequence("text")?.toString()?.trim().orEmpty()
            text = if (senderPrefix.isNotBlank() && msgBody.isNotBlank()) {
                "$senderPrefix: $msgBody"
            } else {
                msgBody
            }
        }

        // Skip generic summary placeholders like "2 new messages" when there's no real content
        val isSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isSummary && text.isBlank()) return null
        if (title.isBlank() && text.isBlank()) return null

        return CapturedNotification(
            key = sbn.key,
            source = source,
            packageName = sbn.packageName,
            sender = title.ifBlank { source },
            text = text.ifBlank { title },
            timestampMs = sbn.postTime,
            contentIntent = sbn.notification.contentIntent
        )
    }

    private fun classifySource(pkg: String, notification: Notification): String? {
        val lower = pkg.lowercase()
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()?.lowercase().orEmpty()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()?.lowercase().orEmpty()

        return when {
            lower.contains("telegram") ||
                lower.contains("challegram") ||
                lower.contains("ayugram") ||
                lower.contains("nekogram") ||
                lower.contains("exteragram") ||
                lower.contains("forkgram") ||
                lower.contains("kotatogram") ||
                lower.contains("messenger.web") -> "TG"
            lower.contains("zoom") -> "ZOOM"
            lower.contains("ksu24") || title.contains("ksu24") || text.contains("ksu24") -> "KSU24"
            else -> null
        }
    }
}
