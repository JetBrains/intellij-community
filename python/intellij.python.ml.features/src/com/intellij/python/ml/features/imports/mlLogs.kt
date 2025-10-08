// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.platform.ml.logs.IntelliJFusEventRegister
import com.intellij.python.ml.features.imports.features.FeaturesRegistry
import com.intellij.util.application
import com.jetbrains.mlapi.logs.MLTreeLogger
import kotlin.random.Random


internal object PyCharmImportsRankingLogs : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("pycharm.quickfix.imports", 8, "ML")
  val mlLogger = MLTreeLogger.withOneEvent(
    eventName = "pycharm_import_statements_ranking",
    logsEventRegister = IntelliJFusEventRegister(GROUP),
    treeFeatures = FeaturesRegistry.declarations,
    treeAnalysis = listOf(
      ContextAnalysis.extractFeatureDeclarations(),
      CandidateAnalysis.extractFeatureDeclarations()
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

internal fun getLoggingOption(): LoggingOption {
  if (application.isEAP || application.isUnitTestMode) {
    return LoggingOption.FULL
  }
  val seed = Random.nextInt()
  if ((seed * 37) % 100 <= RELEASE_FULL_LOGGING_PERCENT) return LoggingOption.FULL
  return LoggingOption.NO_TREE
}
