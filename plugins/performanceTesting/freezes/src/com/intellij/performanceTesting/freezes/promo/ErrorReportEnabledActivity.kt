// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.freezes.promo

import com.intellij.diagnostic.ExceptionAutoReportUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.gdpr.showDataSharingOptionsDialog
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.application
import com.jetbrains.performancePlugin.PerformanceTestingBundle

private const val PROMO_SHOWN_KEY = "promo.notification.automatic.error.report.shown"

internal const val FREEZE_COUNT_KEY = "performance.plugin.promo.freeze.count"
internal const val FREEZE_THRESHOLD = 3

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
      }
      else {
          thisLogger().info("New users are notified in welcome screen, skipping")
      }

      ExceptionAutoReportUtil.recordUserNotifiedOfDataCollection()
    }
  }
}

private fun showNotification(project: Project) {
  PropertiesComponent.getInstance().setValue(PROMO_SHOWN_KEY, true)

  val notification = Notification("PerformancePlugin",
                                  PerformanceTestingBundle.message("auto.report.enabled.title"),
                                  NotificationType.INFORMATION)
    .setDisplayId("promo.notification.automatic.error.report")
    .setIcon(AllIcons.Debugger.AttachToProcess)
    .setSuggestionType(true)
    .addAction(NotificationAction.createSimple(PerformanceTestingBundle.message("auto.report.enabled.settings.action")) {
      ExceptionAutoReportUtil.recordUserVisitedConfigure()

      showDataSharingOptionsDialog()
    })
    .addAction(NotificationAction.createExpiring(PerformanceTestingBundle.message("auto.report.enabled.ok.thanks")) { _, _ -> })

  notification.notify(project)
}