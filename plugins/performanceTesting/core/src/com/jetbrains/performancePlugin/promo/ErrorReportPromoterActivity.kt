// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.promo

import com.intellij.diagnostic.ExceptionAutoReportUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.IdleTracker
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.ui.AppUIUtil
import com.intellij.util.Time
import com.intellij.util.application
import com.jetbrains.performancePlugin.PerformanceTestingBundle

private const val PROMO_SHOWN_KEY = "promo.notification.automatic.error.report.shown"
internal const val FREEZE_THRESHOLD = 3

internal class ErrorReportPromoterActivity : ProjectActivity {
  init {
    if (application.isHeadlessEnvironment() || IdeProductMode.isBackend) throw ExtensionNotApplicableException.create()
  }

  override suspend fun execute(project: Project) {
    if (!isEligible()) {
      thisLogger().debug("User is not eligible for promo, do not wait for idle")
      return
    }

    lateinit var token: AccessToken
    val idleTimeoutMs = Registry.intValue("platform.feedback.time.to.show.notification", 600) * Time.SECOND
    token = IdleTracker.getInstance().addIdleListener(idleTimeoutMs) {
      if (showNotification(project)) {
        token.finish()
      }
    }
  }

  private fun isEligible(): Boolean {
    return ExceptionAutoReportUtil.isAutoReportVisible
           && !ExceptionAutoReportUtil.isAutoReportAllowedByUser()
           && !isAlreadyShown()
  }

  private fun isAlreadyShown(): Boolean {
    return PropertiesComponent.getInstance().getBoolean(PROMO_SHOWN_KEY, false)
  }

  private fun shouldOfferReporting(): Boolean {
    val freezeCount = PropertiesComponent.getInstance().getInt(FREEZE_COUNT_KEY, 0)
    thisLogger().debug("Freeze count: $freezeCount, threshold: $FREEZE_THRESHOLD")

    return freezeCount >= FREEZE_THRESHOLD
  }

  private fun showNotification(project: Project): Boolean {
    if (project.isDisposed || !isEligible()) return true
    if (!shouldOfferReporting()) return false // not yet, try again later

    PropertiesComponent.getInstance().setValue(PROMO_SHOWN_KEY, true)

    val notification = Notification("PerformancePlugin",
                                    PerformanceTestingBundle.message("promo.error.report.title"),
                                    NotificationType.INFORMATION)
      .setDisplayId("promo.notification.automatic.error.report")
      .setIcon(AllIcons.Debugger.AttachToProcess)
      .setSuggestionType(true)
      .addAction(NotificationAction.createExpiring(PerformanceTestingBundle.message("promo.error.report.action.enable")) { _, _ ->
        ExceptionAutoReportUtil.enablingAutoReportOffered(true)

        Notification("Error Report",
                     PerformanceTestingBundle.message("auto.report.enabled.title"),
                     PerformanceTestingBundle.message("auto.report.enabled.text"),
                     NotificationType.INFORMATION)
          .addAction(NotificationAction.createSimple(PerformanceTestingBundle.message("auto.report.enabled.settings.action")) {
            AppUIUtil.confirmConsentOptions(AppUIUtil.loadConsentsForEditing())
          })
          .notify(project)
      })
      .addAction(NotificationAction.createExpiring(PerformanceTestingBundle.message("promo.error.report.action.no.thanks")) { _, _ -> })

    notification.notify(project)
    return true
  }
}
