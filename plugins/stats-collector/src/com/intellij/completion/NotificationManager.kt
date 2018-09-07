// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.reporting.isSendAllowed

class NotificationManager : StartupActivity {
  companion object {
    private const val PLUGIN_NAME = "Completion Stats Collector"
    private const val MESSAGE_TEXT =
      "Data about your code completion usage will be anonymously reported. " +
      "No personal data or code will be sent."

    private const val MESSAGE_TEXT_EAP = "$MESSAGE_TEXT This is only enabled in EAP builds."

    private const val MESSAGE_SHOWN_KEY = "completion.stats.allow.message.shown"
  }

  private fun isMessageShown() = PropertiesComponent.getInstance().getBoolean(MESSAGE_SHOWN_KEY, false)

  private fun setMessageShown(value: Boolean) = PropertiesComponent.getInstance().setValue(MESSAGE_SHOWN_KEY, value)

  override fun runActivity(project: Project) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    // Show message in EAP build or if additional plugin installed
    if (!isMessageShown() && isSendAllowed()) {
      notify(project)
      setMessageShown(true)
    }
  }

  private fun notify(project: Project) {
    val messageText = if (ApplicationManager.getApplication().isEAP) MESSAGE_TEXT_EAP else MESSAGE_TEXT
    val notification = Notification(PLUGIN_NAME, PLUGIN_NAME, messageText, NotificationType.INFORMATION)
    notification.notify(project)
  }
}