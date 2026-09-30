// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.freezes.promo

import com.intellij.diagnostic.ExceptionAutoReportUtil
import com.intellij.ide.gdpr.showDataSharingOptionsDialog
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.application
import com.jetbrains.performancePlugin.PerformanceTestingBundle

internal class ErrorReportEnabledActivity : ProjectActivity {
  init {
    if (application.isHeadlessEnvironment) throw ExtensionNotApplicableException.create()
  }

  override suspend fun execute(project: Project) {
    if (!ExceptionAutoReportUtil.isAutoReportVisible()) return

    if (ExceptionAutoReportUtil.isAutoReportForced) {
      thisLogger().debug("Reporting is forced by settings")
      return
    }

    if (!ExceptionAutoReportUtil.isUserNotifiedOfDataCollection()) {
      if (!ExceptionAutoReportUtil.isAutoReportAllowedByUser()) return

      if (!InitialConfigImportState.isNewUser()) {
        thisLogger().info("Notify user that error reports are sent automatically")

        showNotification(project)

        ExceptionAutoReportUtil.recordUserNotifiedOfDataCollection()
      }
      else {
        thisLogger().info("New users are notified in welcome screen, skipping")
      }
    }
  }
}

private fun showNotification(project: Project) {
  if (ApplicationManagerEx.isInIntegrationTest()) return // do not show sporadically in integration tests

  val notification = Notification("PerformancePlugin",
                                  PerformanceTestingBundle.message("auto.report.enabled.title"),
                                  PerformanceTestingBundle.message("auto.report.enabled.description"),
                                  NotificationType.INFORMATION)
    .setDisplayId("automatic.error.report.enabled")
    .addAction(NotificationAction.createSimple(PerformanceTestingBundle.message("auto.report.enabled.settings.action")) {
      ExceptionAutoReportUtil.recordUserVisitedConfigure()

      showDataSharingOptionsDialog()
    })

  notification.notify(project)
}