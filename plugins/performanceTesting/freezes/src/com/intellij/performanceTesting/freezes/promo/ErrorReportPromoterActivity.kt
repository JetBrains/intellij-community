// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.freezes.promo

import com.intellij.diagnostic.ExceptionAutoReportUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.IdleTracker
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.ui.AppUIUtil
import com.intellij.util.Time
import com.intellij.util.application
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val idleTimeoutMs = RegistryManager.getInstanceAsync().intValue("platform.feedback.time.to.show.notification", 600) * Time.SECOND
    token = IdleTracker.getInstance().addIdleListener(idleTimeoutMs) {
      project.service<ErrorReportPromoterService>().processIdleEvent(project, token)
    }
  }
}

private suspend fun isEligible(): Boolean {
  return ExceptionAutoReportUtil.isAutoReportVisible()
         && !ExceptionAutoReportUtil.isAutoReportAllowedByUser()
         && !isAlreadyShown()
}

private fun isAlreadyShown(): Boolean {
  return PropertiesComponent.getInstance().getBoolean(PROMO_SHOWN_KEY, false)
}

@Service(Service.Level.PROJECT)
private class ErrorReportPromoterService(private val coroutineScope: CoroutineScope) {
  fun processIdleEvent(project: Project, token: AccessToken) {
    coroutineScope.launch {
      showNotification(project, token)
    }
  }

  private suspend fun showNotification(project: Project, token: AccessToken) {
    if (!isEligible()) {
      token.finish()
      return
    }

    if (!shouldOfferReporting()) return // not yet, try again later

    PropertiesComponent.getInstance().setValue(PROMO_SHOWN_KEY, true)

    val notification = Notification("PerformancePlugin",
                                    PerformanceTestingBundle.message("promo.error.report.title"),
                                    NotificationType.INFORMATION)
      .setDisplayId("promo.notification.automatic.error.report")
      .setIcon(AllIcons.Debugger.AttachToProcess)
      .setSuggestionType(true)
      .addAction(NotificationAction.createExpiring(PerformanceTestingBundle.message("promo.error.report.action.enable")) { _, _ ->
        coroutineScope.launch {
          ExceptionAutoReportUtil.enablingAutoReportOffered(true)

          withContext(Dispatchers.EDT) {
            Notification("Error Report",
                         PerformanceTestingBundle.message("auto.report.enabled.title"),
                         PerformanceTestingBundle.message("auto.report.enabled.text"),
                         NotificationType.INFORMATION)
              .addAction(NotificationAction.createSimple(PerformanceTestingBundle.message("auto.report.enabled.settings.action")) {
                AppUIUtil.confirmConsentOptions(AppUIUtil.loadConsentsForEditing())
              })
              .notify(project)
          }
        }
      })
      .addAction(NotificationAction.createExpiring(PerformanceTestingBundle.message("promo.error.report.action.no.thanks")) { _, _ -> })

    notification.notify(project)
    token.finish()
  }

  private fun shouldOfferReporting(): Boolean {
    val freezeCount = PropertiesComponent.getInstance().getInt(FREEZE_COUNT_KEY, 0)
    thisLogger().debug("Freeze count: $freezeCount, threshold: $FREEZE_THRESHOLD")

    return freezeCount >= FREEZE_THRESHOLD
  }
}