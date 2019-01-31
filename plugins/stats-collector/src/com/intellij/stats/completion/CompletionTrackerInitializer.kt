// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.tracker.PositionTrackingListener
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.reporting.isSendAllowed
import com.intellij.reporting.isUnitTestMode
import com.intellij.stats.experiment.WebServiceStatus
import com.intellij.stats.personalization.UserFactorDescriptions
import com.intellij.stats.personalization.UserFactorStorage
import com.intellij.stats.personalization.UserFactorsManager
import java.beans.PropertyChangeListener

class CompletionTrackerInitializer(experimentHelper: WebServiceStatus) : Disposable, BaseComponent {
  companion object {
    // Log only 10% of all completion sessions
    private const val SKIP_SESSIONS_BEFORE_LOG_IN_EAP = 10
    var isEnabledInTests: Boolean = false
  }

  private var loggingStrategy: LoggingStrategy = createDefaultLoggingStrategy(experimentHelper)
  private val actionListener = LookupActionsListener()
  private val lookupTrackerInitializer = PropertyChangeListener {
    val lookup = it.newValue
    if (lookup == null) {
      actionListener.listener = CompletionPopupListener.Adapter()
    }
    else if (lookup is LookupImpl) {
      if (isUnitTestMode() && !isEnabledInTests) return@PropertyChangeListener
      lookup.putUserData(CompletionUtil.COMPLETION_STARTING_TIME_KEY, System.currentTimeMillis())

      val globalStorage = UserFactorStorage.getInstance()
      val projectStorage = UserFactorStorage.getInstance(lookup.project)

      val userFactors = UserFactorsManager.getInstance(lookup.project).getAllFactors()
      val userFactorValues = mutableMapOf<String, String?>()
      userFactors.asSequence().map { "${it.id}:App" to it.compute(globalStorage) }.toMap(userFactorValues)
      userFactors.asSequence().map { "${it.id}:Project" to it.compute(projectStorage) }.toMap(userFactorValues)

      lookup.putUserData(UserFactorsManager.USER_FACTORS_KEY, userFactorValues)
      val shownTimesTracker = PositionTrackingListener(lookup)
      lookup.setPrefixChangeListener(shownTimesTracker)

      UserFactorStorage.applyOnBoth(lookup.project, UserFactorDescriptions.COMPLETION_USAGE) {
        it.fireCompletionUsed()
      }

      if (loggingStrategy.shouldBeLogged(lookup, experimentHelper)) {
        val tracker = actionsTracker(lookup, experimentHelper)
        actionListener.listener = tracker
        lookup.addLookupListener(tracker)
        lookup.setPrefixChangeListener(tracker)
      }

      // setPrefixChangeListener has addPrefixChangeListener semantics
      lookup.setPrefixChangeListener(TimeBetweenTypingTracker(lookup.project))
      lookup.addLookupListener(LookupCompletedTracker())
      lookup.addLookupListener(LookupStartedTracker())
    }
  }

  @Suppress("unused")
  fun setLoggingStrategy(strategy: LoggingStrategy) {
    loggingStrategy = strategy
  }

  private fun actionsTracker(lookup: LookupImpl, experimentHelper: WebServiceStatus): CompletionActionsTracker {
    val logger = CompletionLoggerProvider.getInstance().newCompletionLogger()
    return CompletionActionsTracker(lookup, logger, experimentHelper)
  }

  private fun shouldInitialize() = isSendAllowed() || isUnitTestMode()

  private fun createDefaultLoggingStrategy(experimentHelper: WebServiceStatus): LoggingStrategy {
    val application = ApplicationManager.getApplication()

    if (application.isUnitTestMode) return LogAllSessions

    if (!application.isEAP) return LogNothing

    val experimentVersion = experimentHelper.experimentVersion()
    if (PluginManager.BUILD_NUMBER.contains("-183") && experimentVersion == 5 || experimentVersion == 6) return LogAllSessions

    return LogEachN(SKIP_SESSIONS_BEFORE_LOG_IN_EAP)
  }

  override fun initComponent() {
    if (!shouldInitialize()) return

    val busConnection = ApplicationManager.getApplication().messageBus.connect(this)
    busConnection.subscribe(AnActionListener.TOPIC, actionListener)
    busConnection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectOpened(project: Project) {
        val lookupManager = LookupManager.getInstance(project)
        lookupManager.addPropertyChangeListener(lookupTrackerInitializer)
      }

      override fun projectClosed(project: Project) {
        val lookupManager = LookupManager.getInstance(project)
        lookupManager.removePropertyChangeListener(lookupTrackerInitializer)
      }
    })
  }

  override fun dispose() {
  }
}