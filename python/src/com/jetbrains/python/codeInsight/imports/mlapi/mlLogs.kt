// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.ml.impl.logs.MLEventLoggerProvider.Companion.ML_RECORDER_ID
import com.intellij.platform.ml.impl.tools.registerMLTaskLogging
import com.intellij.util.application
import com.jetbrains.ml.MLUnit
import com.jetbrains.ml.logs.*
import com.jetbrains.ml.logs.schema.BooleanEventField
import com.jetbrains.ml.logs.schema.EventField
import com.jetbrains.ml.logs.schema.EventPair
import com.jetbrains.ml.model.MLModel
import com.jetbrains.ml.session.MLSession
import com.jetbrains.ml.session.MLSessionInfo
import com.jetbrains.ml.tree.LevelFeaturesSchema
import com.jetbrains.ml.tree.MLTree
import com.jetbrains.python.PythonPluginDisposable


@Service
private class EventLogGroupHolder {
  val group by lazy {
    EventLogGroup("pycharm.quickfix.imports", 3, ML_RECORDER_ID).also {
      it.registerMLTaskLogging(service<MLTaskPyCharmImportStatementsRanking>().task,
                               parentDisposable = service<PythonPluginDisposable>())
    }
  }
}

class PyCharmImportsRankingLogs : CounterUsagesCollector() {
  override fun getGroup() = service<EventLogGroupHolder>().group
}

internal enum class LoggingOption {
  FULL,
  NO_TREE,
  SKIP
}

internal const val RELEASE_LOGGING_PERCENT = 5

internal fun getLoggingOption(mlSession: MLSession<*, *>): LoggingOption {
  if (application.isEAP || application.isUnitTestMode) {
    return LoggingOption.FULL
  }
  if (mlSession.id % 100 > RELEASE_LOGGING_PERCENT) return LoggingOption.SKIP
  return LoggingOption.NO_TREE
}

object PyCharmImportsRankingLogger : MLSessionLoggerProvider<Any> {
  private val baseLoggerProvider = EntireSessionLoggerProvider<Any, Boolean>(BooleanEventField("prediction") { "ML model prediction" }) { null }

  override fun createMLSessionLogger(
    eventPrefix: String,
    taskId: String,
    treeAnalysisDeclaration: Map<MLUnit<*>, List<EventField<*>>>,
    sessionAnalysisDeclaration: List<EventField<*>>,
    featuresDeclaration: List<LevelFeaturesSchema>,
    fusEventRegister: FusEventRegister,
  ): MLSessionLogger<Any> {
    val baseSessionLogger = baseLoggerProvider.createMLSessionLogger(eventPrefix, taskId, treeAnalysisDeclaration, sessionAnalysisDeclaration, featuresDeclaration, fusEventRegister)
    return object : MLSessionLogger<Any> {
      override suspend fun logMLSession(sessionInfo: MLSessionInfo<out MLModel<Any>, Any>, analysisException: Throwable?, sessionAnalysis: List<EventPair<*>>, structure: MLTree.ATopNode<out MLModel<Any>, Any>?): MLSessionLoggingOutcome {
        val loggingOption = checkNotNull(sessionAnalysis.find { it.field == ML_LOGGING_STATE }).data as LoggingOption
        return when (loggingOption) {
          LoggingOption.FULL -> baseSessionLogger.logMLSession(sessionInfo, analysisException, sessionAnalysis, structure)
          LoggingOption.NO_TREE -> {
            baseSessionLogger.logMLSession(sessionInfo, analysisException, sessionAnalysis, null)
            MLSessionLoggingOutcome.Custom("no tree logged")
          }
          LoggingOption.SKIP -> MLSessionLoggingOutcome.FilteredOut
        }
      }
    }
  }
}
