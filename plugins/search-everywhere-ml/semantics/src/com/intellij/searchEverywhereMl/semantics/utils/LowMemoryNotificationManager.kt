package com.intellij.searchEverywhereMl.semantics.utils

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.searchEverywhereMl.semantics.SemanticSearchBundle
import java.util.concurrent.atomic.AtomicBoolean

@Service
class LowMemoryNotificationManager {
  private var isShownAndNotExpired = AtomicBoolean(false)

  fun showNotification() {
    // Make sure a user does not receive many notifications during the search.
    if (isShownAndNotExpired.compareAndSet(false, true)) {
      createNotification().notify(null)
    }
  }

  private fun createNotification(): Notification {
    val notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(
        SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.indexing.low.memory"),
        "", NotificationType.WARNING
      )

    notification.whenExpired {
      // User may struggle to find settings again; that's why we allow to open settings from the notification again
      isShownAndNotExpired.compareAndSet(true, false)
    }

    return notification
  }

  companion object {
    private const val NOTIFICATION_GROUP_ID = "Semantic search"

    fun getInstance() = service<LowMemoryNotificationManager>()
  }
}