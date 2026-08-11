package com.retro.minimallauncher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class NotificationSummary(
    val messageCount: Int = 0,
    val missedCallCount: Int = 0,
    val connected: Boolean = false
)

object NotificationSummaryStore {
    var summary by mutableStateOf(NotificationSummary())
        private set

    fun update(messageCount: Int, missedCallCount: Int, connected: Boolean) {
        summary = NotificationSummary(messageCount, missedCallCount, connected)
    }
}

class RetroNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshCounts()
    }

    override fun onListenerDisconnected() {
        NotificationSummaryStore.update(0, 0, false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshCounts()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshCounts()
    }

    private fun refreshCounts() {
        val notifications = runCatching { activeNotifications?.toList().orEmpty() }
            .getOrDefault(emptyList())

        var messages = 0
        var missedCalls = 0

        notifications.forEach { sbn ->
            val notification = sbn.notification ?: return@forEach
            if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return@forEach
            if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return@forEach

            when (notification.category) {
                Notification.CATEGORY_MESSAGE -> messages++
                Notification.CATEGORY_MISSED_CALL -> missedCalls++
            }
        }

        NotificationSummaryStore.update(messages, missedCalls, true)
    }
}
