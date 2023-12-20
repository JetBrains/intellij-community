// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.tracker

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.experiment.ExperimentInfo
import com.intellij.completion.ml.experiment.ExperimentStatus
import com.intellij.completion.ml.storage.MutableLookupStorage
import com.intellij.completion.ml.tracker.LookupTracker
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project
import com.intellij.stats.completion.CompletionStatsPolicy
import com.intellij.stats.completion.sender.isCompletionLogsSendAllowed
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.ThreadingAssertions
import kotlin.random.Random

class CompletionLoggerInitializer : LookupTracker() {
  companion object {
    private const val COMPLETION_EVALUATION_HEADLESS = "completion.evaluation.headless"
    private const val PYTHON_IN_DATASPELL = "python dataspell"

    private fun shouldInitialize(): Boolean {
      val app = ApplicationManager.getApplication()
      return app.isEAP && StatisticsUploadAssistant.isSendAllowed() || app.isHeadlessEvaluation() || app.isUnitTestMode
    }

    private val LOGGED_SESSIONS_RATIO_LANGUAGE: Map<String, Double> = mapOf(
      "python" to 0.5,
      PYTHON_IN_DATASPELL to 1.0,
      "scala" to 0.3,
      "php" to 0.2,
      "kotlin" to 0.2,
      "java" to 0.1,
      "javascript" to 0.2,
      "typescript" to 0.5,
      "c/c++" to 0.5,
      "c#" to 0.05,
      "go" to 0.4,
      "rust" to 0.1
    )

    private val LOGGED_SESSIONS_RATIO_FILE_TYPE: Map<String, Double> = mapOf(
      "ipynb" to 1.0,
    )

    private fun Application.isHeadlessEvaluation(): Boolean = isHeadlessEnvironment &&
                                                              java.lang.Boolean.getBoolean(COMPLETION_EVALUATION_HEADLESS)
  }

  private val actionListener: LookupActionsListener by lazy { LookupActionsListener.getInstance() }

  override fun lookupClosed() {
    ThreadingAssertions.assertEventDispatchThread()
    actionListener.listener = CompletionPopupListener.DISABLED
  }

  override fun lookupCreated(lookup: LookupImpl,
                             storage: MutableLookupStorage) {
    ThreadingAssertions.assertEventDispatchThread()
    if (!shouldInitialize()) return

    val experimentInfo = ExperimentStatus.getInstance().forLanguage(storage.language)
    if (sessionShouldBeLogged(experimentInfo, storage.language, lookup)) {
      val tracker = actionsTracker(lookup, storage, experimentInfo)
      actionListener.listener = tracker
      lookup.addLookupListener(tracker)
      lookup.setPrefixChangeListener(tracker)
      storage.markLoggingEnabled()
    }
    else {
      actionListener.listener = CompletionPopupListener.DISABLED
    }
  }

  private fun actionsTracker(lookup: LookupImpl,
                             storage: MutableLookupStorage,
                             experimentInfo: ExperimentInfo): CompletionActionsListener {
    val logger = CompletionLoggerProvider.getInstance().newCompletionLogger(getLoggingLanguageName(storage.language),
                                                                            shouldLogElementFeatures(storage.language, lookup.project))
    val actionsTracker = CompletionActionsTracker(lookup, storage, logger, experimentInfo)
    return LoggerPerformanceTracker(actionsTracker, storage.performanceTracker)
  }

  private fun shouldLogElementFeatures(language: Language, project: Project): Boolean =
    ExperimentStatus.getInstance().forLanguage(language).shouldLogElementFeatures(project)

  private fun sessionShouldBeLogged(experimentInfo: ExperimentInfo, language: Language, lookup: LookupImpl): Boolean {
    if (CompletionStatsPolicy.isStatsLogDisabled(language) || !getPluginInfo(language::class.java).isSafeToReport()) return false

    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode || experimentInfo.shouldLogSessions(lookup.project)) return true
    if (application.isHeadlessEvaluation()) return true

    if (!isCompletionLogsSendAllowed()) {
      return false
    }

    val logSessionChance = lookup.psiFile?.fileType?.defaultExtension?.let { LOGGED_SESSIONS_RATIO_FILE_TYPE[it] }
                           ?: LOGGED_SESSIONS_RATIO_LANGUAGE.getOrDefault(getLoggingLanguageName(language).lowercase(), 1.0)
    return Random.nextDouble() < logSessionChance
  }

  private fun getLoggingLanguageName(language: Language): String {
    Language.findLanguageByID("JavaScript")?.let { js ->
      if (language.isKindOf(js) && !language.displayName.contains("TypeScript", ignoreCase = true)) {
        return "JavaScript"
      }
    }
    Language.findLanguageByID("SQL")?.let { sql ->
      if (language.isKindOf(sql)) {
        return sql.displayName
      }
    }

    if (PlatformUtils.isDataSpell() && language.displayName.contains("python", ignoreCase = true)) {
      return PYTHON_IN_DATASPELL
    }

    return language.displayName
  }

  private class LookupActionsListener private constructor() : AnActionListener {
    companion object {
      private val LOG = logger<LookupActionsListener>()
      private val instance = LookupActionsListener()
      private var subscribed = false

      fun getInstance(): LookupActionsListener {
        if (!subscribed) {
          ApplicationManager.getApplication().messageBus.connect().subscribe(AnActionListener.TOPIC, instance)
          subscribed = true
        }
        return instance
      }
    }

    private val down by lazy { ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN) }
    private val up by lazy { ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP) }
    private val backspace by lazy { ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_BACKSPACE) }

    var listener: CompletionPopupListener = CompletionPopupListener.DISABLED

    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
      LOG.runAndLogException {
        when (action) {
          down -> listener.downPressed()
          up -> listener.upPressed()
          backspace -> listener.afterBackspacePressed()
        }
      }
    }

    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
      LOG.runAndLogException {
        when (action) {
          down -> listener.beforeDownPressed()
          up -> listener.beforeUpPressed()
          backspace -> listener.beforeBackspacePressed()
        }
      }
    }

    override fun beforeEditorTyping(c: Char, dataContext: DataContext) {
      LOG.runAndLogException {
        listener.beforeCharTyped(c)
      }
    }
  }
}
