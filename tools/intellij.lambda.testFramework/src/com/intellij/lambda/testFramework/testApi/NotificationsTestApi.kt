package com.intellij.lambda.testFramework.testApi

import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.notification.ActionCenter
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.remoteDev.tests.LambdaIdeContext
import com.intellij.remoteDev.tests.impl.utils.waitSuspendingNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun LambdaIdeContext.findNotification(content: String, checkExactMatch: Boolean = true): Notification? {
  val project = getProjectOrNull() ?: return null
  return ActionCenter.getNotifications(project)
    .also { frameworkLogger.info("all notifications = $it") }
    .find {
      when (checkExactMatch) {
        true -> it.content == content || it.title == content
        else -> it.content.contains(content) || it.title.contains(content)
      }
    }
}

suspend fun LambdaIdeContext.waitForNotification(content: String, timeout: Duration = 10.seconds, checkExactMatch: Boolean = true) =
  waitSuspendingNotNull("Notification with content '$content' is found", timeout) {
    findNotification(content = content, checkExactMatch = checkExactMatch)
  }

suspend fun waitTillNotificationIsExpired(notification: Notification, timeout: Duration) =
  waitSuspendingNotNull("Notification is expired", timeout) { notification.isExpired }

fun getNotificationDisplayType(notification: Notification) =
  NotificationGroupManager.getInstance().getNotificationGroup(notification.groupId).displayType
