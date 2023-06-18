// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.python.PyBundle
import com.jetbrains.python.black.configuration.BlackFormatterConfigurable
import com.jetbrains.python.black.configuration.BlackFormatterConfiguration
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
@Service(Service.Level.APP)
class BlackFormatterAdvertiserService private constructor() {

  companion object {
    @NonNls
    const val SHOW_BLACK_FORMATTER_SUPPORT_NOTIFICATION = "black.formatter.show.support.notification"

    fun getInstance(): BlackFormatterAdvertiserService =
      ApplicationManager.getApplication().getService(BlackFormatterAdvertiserService::class.java)
  }

  private var alreadyShown: Boolean = false

  fun suggestBlack(project: Project, blackFormatterConfiguration: BlackFormatterConfiguration) {
    if (blackFormatterConfiguration == BlackFormatterConfiguration() && !alreadyShown) {
      if (BlackFormatterUtil.isBlackFormatterInstalledOnProjectSdk(blackFormatterConfiguration.getSdk(project))) {
        showBlackFormatterSupportNotification(project,
                                              PyBundle.message("black.advertising.service.found.in.packages"))
      }
      else if (BlackFormatterUtil.isBlackExecutableDetected()) {
        showBlackFormatterSupportNotification(project,
                                              PyBundle.message("black.advertising.service.found.in.PATH",
                                                                      if (SystemInfo.isWindows) 0 else 1))
      }
    }
  }

  @Synchronized
  private fun showBlackFormatterSupportNotification(project: Project, @Nls message: String) {
    val propertiesComponent = PropertiesComponent.getInstance()
    if (!propertiesComponent.getBoolean(SHOW_BLACK_FORMATTER_SUPPORT_NOTIFICATION, true)) {
      return
    }

    val notification = NotificationGroupManager.getInstance().getNotificationGroup(BlackFormattingService.NOTIFICATION_GROUP_ID)
      .createNotification(PyBundle.message("black.advertising.service.notification.title"), message, NotificationType.INFORMATION)
      .setDisplayId("black.formatter")
      .setSuggestionType(true)
      .setImportantSuggestion(true)
      .addAction(NotificationAction
                   .createSimpleExpiring(PyBundle.message("black.advertising.service.configure.button.label")) {
                     ShowSettingsUtil.getInstance().showSettingsDialog(project, BlackFormatterConfigurable::class.java)
                   })
      .addAction(NotificationAction
                   .createSimpleExpiring(PyBundle.message("black.advertising.service.dont.show.again.label")) {
                     propertiesComponent.setValue(
                       SHOW_BLACK_FORMATTER_SUPPORT_NOTIFICATION,
                       "false", "true")
                   })
    notification.notify(project)
    alreadyShown = true
  }
}