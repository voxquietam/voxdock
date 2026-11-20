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

        val previewLines = mutableListOf<String>()

        // Collect all messages from MessagingStyle (unread history in Telegram without marking read)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            val msgs = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            msgs?.forEach { raw ->
                val bundle = raw as? android.os.Bundle ?: return@forEach
                val senderPrefix = bundle.getCharSequence("sender")?.toString()?.trim().orEmpty()
                val msgBody = bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
                val formatted = if (senderPrefix.isNotBlank() && msgBody.isNotBlank()) {
                    "$senderPrefix: $msgBody"
                } else {
                    msgBody
                }
                if (formatted.isNotBlank()) previewLines.add(formatted)
            }
        }

        if (previewLines.isEmpty()) {
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            lines?.forEach { cs ->
                val s = cs.toString().trim()
                if (s.isNotBlank()) previewLines.add(s)
            }
        }

        if (text.isBlank() && previewLines.isNotEmpty()) {
            text = previewLines.last()
        }
        if (previewLines.isEmpty() && text.isNotBlank()) {
            previewLines.add(text)
        }
        // Deduplicate near-identical entries (e.g. Telegram photo caption + text duplicate)
        val dedupedPreview = mutableListOf<String>()
        for (line in previewLines) {
            val norm = line.replace("🖼️", "").replace(Regex("\\s+"), " ").trim()
            if (dedupedPreview.none { it.replace("🖼️", "").replace(Regex("\\s+"), " ").trim() == norm }) {
                dedupedPreview.add(line)
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
            previewLines = dedupedPreview,
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
