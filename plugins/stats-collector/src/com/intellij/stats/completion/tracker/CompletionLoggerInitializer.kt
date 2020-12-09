// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.tracker

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.tracker.LookupTracker
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.stats.completion.sender.isCompletionLogsSendAllowed
import com.intellij.completion.ml.experiment.ExperimentInfo
import com.intellij.completion.ml.experiment.ExperimentStatus
import com.intellij.completion.ml.storage.MutableLookupStorage
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.project.Project
import com.intellij.stats.completion.CompletionStatsPolicy
import kotlin.random.Random

class CompletionLoggerInitializer(project: Project) : LookupTracker() {
  companion object {
    fun shouldInitialize(): Boolean =
      (ApplicationManager.getApplication().isEAP && StatisticsUploadAssistant.isSendAllowed()) || ApplicationManager.getApplication().isUnitTestMode

    private val LOGGED_SESSIONS_RATIO: Map<String, Double> = mapOf(
      "python" to 0.5,
      "scala" to 0.3,
      "php" to 0.2,
      "kotlin" to 0.2,
      "java" to 0.1,
      "ecmascript 6" to 0.2,
      "typescript" to 0.5,
      "c/c++" to 0.5,
      "c#" to 0.1,
      "go" to 0.4
    )
  }
  private val actionListener: LookupActionsListener = LookupActionsListener()

  init {
    if (shouldInitialize()) {
      project.messageBus.connect().subscribe(AnActionListener.TOPIC, actionListener)
    }
  }

  override fun lookupClosed() {
    actionListener.listener = CompletionPopupListener.Adapter()
  }

  override fun lookupCreated(lookup: LookupImpl,
                             storage: MutableLookupStorage) {
    if (!shouldInitialize()) return

    val experimentInfo = ExperimentStatus.getInstance().forLanguage(storage.language)
    if (sessionShouldBeLogged(experimentInfo, storage.language)) {
      val tracker = actionsTracker(lookup, storage, experimentInfo)
      actionListener.listener = tracker
      lookup.addLookupListener(tracker)
      lookup.setPrefixChangeListener(tracker)
      storage.markLoggingEnabled()
    }
    else {
      actionListener.listener = CompletionPopupListener.Adapter()
    }
  }

  private fun actionsTracker(lookup: LookupImpl,
                             storage: MutableLookupStorage,
                             experimentInfo: ExperimentInfo): CompletionActionsListener {
    val logger = CompletionLoggerProvider.getInstance().newCompletionLogger(storage.language)
    val actionsTracker = CompletionActionsTracker(lookup, storage, logger, experimentInfo)
    return LoggerPerformanceTracker(actionsTracker, storage.performanceTracker)
  }

  private fun sessionShouldBeLogged(experimentInfo: ExperimentInfo, language: Language): Boolean {
    if (CompletionStatsPolicy.isStatsLogDisabled(language) || !getPluginInfo(language::class.java).isSafeToReport()) return false
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode || experimentInfo.inExperiment) return true

    if (!isCompletionLogsSendAllowed()) {
      return false
    }

    val logSessionChance = LOGGED_SESSIONS_RATIO.getOrDefault(language.displayName.toLowerCase(), 1.0)
    return Random.nextDouble() < logSessionChance
  }
}
