// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.sorting

import com.intellij.application.options.CodeCompletionOptions
import com.intellij.completion.StatsCollectorBundle
import com.intellij.completion.settings.CompletionMLRankingSettings
import com.intellij.ide.util.PropertiesComponent
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
import com.intellij.stats.experiment.ExperimentStatus
import com.jetbrains.completion.ranker.WeakModelProvider
import org.jetbrains.annotations.TestOnly

object RankingSupport {
  private const val LANGUAGES_RANKING_UPDATED_PROPERTY_KEY = "ml.completion.experiment.languages.ranking.updated"
  private val LOG = logger<RankingSupport>()
  private val properties = PropertiesComponent.getInstance()
  private var enabledInTests: Boolean = false

  fun getRankingModel(language: Language): RankingModelWrapper? {
    val provider = findProviderSafe(language)
    return if (provider != null && shouldSortByML(language, provider)) tryGetModel(provider) else null
  }

  fun availableLanguages(): List<String> {
    val registeredLanguages = Language.getRegisteredLanguages()
    return WeakModelProvider.availableProviders()
      .filter { provider ->
        registeredLanguages.any {
          provider.isLanguageSupported(it)
        }
      }.map { it.displayNameInSettings }.distinct().sorted().toList()
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
      LOG.error("Could not create ranking model '${provider.displayNameInSettings}'", e)
      return null
    }
  }

  private fun shouldSortByML(language: Language, provider: RankingModelProvider): Boolean {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode) return enabledInTests
    val experimentStatus = ExperimentStatus.getInstance()
    if (application.isEAP && experimentStatus.isExperimentOnCurrentIDE(language) && isCompletionLogsSendAllowed()) {
      configureSettingsInExperiment(experimentStatus, language, provider.displayNameInSettings)
    }

    val settings = CompletionMLRankingSettings.getInstance()
    return settings.isRankingEnabled && settings.isLanguageEnabled(provider.displayNameInSettings)
  }

  private fun configureSettingsInExperiment(experimentStatus: ExperimentStatus, language: Language, languageName: String) {
    val shouldRank = experimentStatus.shouldRank(language)
    val shouldShowArrows = experimentStatus.shouldShowArrows(language)
    val settings = CompletionMLRankingSettings.getInstance()
    if (experimentStatus.experimentChanged()) {
      updateSettingsOnce(settings, shouldRank, shouldShowArrows)
    }
    val languages = properties.getValues(LANGUAGES_RANKING_UPDATED_PROPERTY_KEY) ?: emptyArray()
    if (languageName !in languages) {
      settings.setLanguageEnabled(languageName, shouldRank)
      properties.setValues(LANGUAGES_RANKING_UPDATED_PROPERTY_KEY, languages + arrayOf(languageName))
    }
  }

  private fun updateSettingsOnce(settings: CompletionMLRankingSettings, shouldRank: Boolean, shouldShowArrows: Boolean) {
    settings.isRankingEnabled = shouldRank
    val languages = availableLanguages()
    languages.forEach { settings.setLanguageEnabled(it, shouldRank) }
    settings.isShowDiffEnabled = shouldShowArrows
    if (shouldShowArrows) {
      showNotificationAboutArrows()
    }
    properties.setValues(LANGUAGES_RANKING_UPDATED_PROPERTY_KEY, languages.toTypedArray())
  }

  private fun showNotificationAboutArrows() {
    Notification(
      StatsCollectorBundle.message("ml.completion.show.diff.notification.groupId"),
      StatsCollectorBundle.message("ml.completion.show.diff.notification.title"),
      StatsCollectorBundle.message("ml.completion.show.diff.notification.content"),
      NotificationType.INFORMATION
    ).addAction(object : NotificationAction("OK") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          notification.expire()
        }
      })
      .addAction(object : NotificationAction("Disable") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          CompletionMLRankingSettings.getInstance().isShowDiffEnabled = false
          notification.expire()
        }
      })
      .addAction(object : NotificationAction("Open settings...") {
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
