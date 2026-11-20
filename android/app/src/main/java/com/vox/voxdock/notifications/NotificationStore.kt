package com.vox.voxdock.notifications

import android.app.PendingIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapturedNotification(
    val key: String,
    val source: String, // "TG" | "ZOOM" | "KSU24" | "OTHER"
    val packageName: String,
    val sender: String,
    val text: String,
    val previewLines: List<String> = emptyList(),
    val timestampMs: Long,
    val contentIntent: PendingIntent?
)

object NotificationStore {
    private val _items = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val items: StateFlow<List<CapturedNotification>> = _items.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    var cancelCallback: ((String) -> Unit)? = null

    fun setConnected(value: Boolean) {
        _connected.value = value
    }

    fun upsert(item: CapturedNotification) {
        val current = _items.value.filterNot {
            it.key == item.key ||
                (it.source == item.source && it.sender == item.sender && it.text == item.text)
        }
        _items.value = (listOf(item) + current).take(50)
    }

    fun removeByKey(key: String) {
        _items.value = _items.value.filterNot { it.key == key }
    }

    fun dismissAndCancel(key: String) {
        removeByKey(key)
        cancelCallback?.invoke(key)
    }

    fun replaceAll(list: List<CapturedNotification>) {
        val deduped = LinkedHashMap<String, CapturedNotification>()
        for (item in list.sortedByDescending { it.timestampMs }) {
            val sig = "${item.source}|${item.sender}|${item.text}"
            if (!deduped.containsKey(sig)) {
                deduped[sig] = item
            }
        }
        _items.value = deduped.values.take(50)
    }
}
