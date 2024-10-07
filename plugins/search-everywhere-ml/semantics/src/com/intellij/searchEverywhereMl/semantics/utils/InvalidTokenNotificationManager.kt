package com.intellij.searchEverywhereMl.semantics.utils

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.notification.Notification
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.RegistryUi
import com.intellij.searchEverywhereMl.semantics.SemanticSearchBundle
import java.util.concurrent.atomic.AtomicBoolean

@Service
class InvalidTokenNotificationManager {
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
        SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.actions.api.authentication.failed"),
        "", NotificationType.WARNING
      )

    notification.addAction(NotificationAction.createSimple(
      SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.open.registry")
    ) {
      RegistryUi().show()
      notification.expire()
    })

    notification.whenExpired {
      // User may struggle to find settings again; that's why we allow to open settings from the notification again
      isShownAndNotExpired.compareAndSet(true, false)
    }

    return notification
  }

  companion object {
    private const val NOTIFICATION_GROUP_ID: String = "Semantic search"

    fun getInstance(): InvalidTokenNotificationManager = service()
  }
}