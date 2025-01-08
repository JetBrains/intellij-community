// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.platform.ml.impl.logs.MLEventLoggerProvider.Companion.ML_RECORDER_ID
import com.intellij.platform.ml.impl.logs.fus.IntelliJFusEventRegister
import com.intellij.util.application
import com.jetbrains.ml.building.blocks.logs.FusLoggerFactories
import com.jetbrains.ml.building.blocks.logs.extractEventFields
import com.jetbrains.ml.building.blocks.task.MLFeaturesTree


internal object PyCharmImportsRankingLogs : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("pycharm.quickfix.imports", 5, ML_RECORDER_ID)
  val mlLogger = FusLoggerFactories
    .withOneEvent(
      fusEventName = "pycharm_import_statements_ranking",
      fusEventRegister = IntelliJFusEventRegister(GROUP),
      treeFeatures = MLTaskPyCharmImportStatementsRanking.treeFeatures,
      treeAnalysisFields = listOf(
        extractEventFields(ContextAnalysis),
        extractEventFields(CandidateAnalysis)
      )
    )

  override fun getGroup() = GROUP
}

internal enum class LoggingOption {
  FULL,
  NO_TREE,
  SKIP
}

internal const val RELEASE_FULL_LOGGING_PERCENT = 10

internal fun getLoggingOption(tree: MLFeaturesTree): LoggingOption {
  if (application.isEAP || application.isUnitTestMode) {
    return LoggingOption.FULL
  }
  if ((tree.sessionId * 37) % 100 <= RELEASE_FULL_LOGGING_PERCENT) return LoggingOption.FULL
  return LoggingOption.NO_TREE
}
