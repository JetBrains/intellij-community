// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.sorting

import com.intellij.application.options.CodeCompletionOptions
import com.intellij.completion.StatsCollectorBundle
import com.intellij.completion.settings.CompletionMLRankingSettings
import com.intellij.internal.ml.completion.RankingModelProvider
import com.intellij.lang.Language
import com.intellij.notification.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.util.Disposer
import com.intellij.stats.sender.isCompletionLogsSendAllowed
import com.intellij.openapi.util.registry.Registry
import com.intellij.stats.experiment.ExperimentInfo
import com.intellij.stats.experiment.ExperimentStatus
import com.jetbrains.completion.ranker.WeakModelProvider
import org.jetbrains.annotations.TestOnly

object RankingSupport {
  private const val SHOW_ARROWS_NOTIFICATION_REGISTRY = "completion.stats.show.arrows.notification"
  private val LOG = logger<RankingSupport>()
  private var enabledInTests: Boolean = false

  fun getRankingModel(language: Language): RankingModelWrapper? {
    val provider = findProviderSafe(language)
    return if (provider != null && shouldSortByML(language, provider)) tryGetModel(provider) else null
  }

  fun availableRankers(): List<RankingModelProvider> {
    val registeredLanguages = Language.getRegisteredLanguages()
    return WeakModelProvider.availableProviders()
      .filter { provider ->
        registeredLanguages.any {
          provider.isLanguageSupported(it)
        }
      }.toList()
  }

  private fun findProviderSafe(language: Language): RankingModelProvider? {
    try {
      return WeakModelProvider.findProvider(language)
    }
    catch (e: IllegalStateException) {
      LOG.error(e)
      return null
    }
  }

  private fun tryGetModel(provider: RankingModelProvider): RankingModelWrapper? {
    try {
      return LanguageRankingModel(provider.model)
    }
    catch (e: Exception) {
      LOG.error("Could not create ranking model with id '${provider.id}' and name '${provider.displayNameInSettings}'", e)
      return null
    }
  }

  private fun shouldSortByML(language: Language, provider: RankingModelProvider): Boolean {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode) return enabledInTests
    val experimentStatus = ExperimentStatus.getInstance()
    val experimentInfo = experimentStatus.forLanguage(language)
    if (application.isEAP && isCompletionLogsSendAllowed() && experimentInfo.inExperiment && experimentStatus.experimentChanged(language)) {
      configureSettingsInExperimentOnce(experimentInfo, provider.id)
    }

    val settings = CompletionMLRankingSettings.getInstance()
    return settings.isRankingEnabled && settings.isLanguageEnabled(provider.id)
  }

  private fun configureSettingsInExperimentOnce(experimentInfo: ExperimentInfo, rankerId: String) {
    val settings = CompletionMLRankingSettings.getInstance()
    if (experimentInfo.shouldRank) settings.isRankingEnabled = experimentInfo.shouldRank
    settings.setLanguageEnabled(rankerId, experimentInfo.shouldRank)
    settings.isShowDiffEnabled = experimentInfo.shouldShowArrows
    if (experimentInfo.shouldShowArrows && shouldShowArrowsNotification()) {
      showNotificationAboutArrows()
    }
  }

  private fun shouldShowArrowsNotification(): Boolean = Registry.`is`(SHOW_ARROWS_NOTIFICATION_REGISTRY, true)

  private fun showNotificationAboutArrows() {
    Notification(
      StatsCollectorBundle.message("ml.completion.show.diff.notification.groupId"),
      StatsCollectorBundle.message("ml.completion.show.diff.notification.title"),
      StatsCollectorBundle.message("ml.completion.show.diff.notification.content"),
      NotificationType.INFORMATION
    ).addAction(object : NotificationAction(StatsCollectorBundle.message("ml.completion.show.diff.notification.ok")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          notification.expire()
        }
      })
      .addAction(object : NotificationAction(StatsCollectorBundle.message("ml.completion.show.diff.notification.disable")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          CompletionMLRankingSettings.getInstance().isShowDiffEnabled = false
          notification.expire()
        }
      })
      .addAction(object : NotificationAction(StatsCollectorBundle.message("ml.completion.show.diff.notification.settings")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          ShowSettingsUtil.getInstance().showSettingsDialog(null, CodeCompletionOptions::class.java)
          notification.expire()
        }
      }).notify(null)
  }

  @TestOnly
  fun enableInTests(parentDisposable: Disposable) {
    enabledInTests = true
    Disposer.register(parentDisposable, Disposable { enabledInTests = false })
  }
}
