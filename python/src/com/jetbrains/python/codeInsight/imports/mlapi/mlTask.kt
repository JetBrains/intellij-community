// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi

import com.intellij.codeInsight.inline.completion.ml.MLUnitTyping
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.ml.impl.tools.IJPlatform
import com.jetbrains.ml.MLTaskBuilder
import kotlin.time.Duration.Companion.days


internal val LONGEST_POPUP_LIFE_DURATION = 365.days

@Service
class MLTaskPyCharmImportStatementsRanking {
  val task by lazy {
    MLTaskBuilder(
      taskId = "pycharm_import_statements_ranking",
      taskLevels = listOf(
        setOf(MLUnitImportCandidatesList, MLUnitTyping),
        setOf(MLUnitImportCandidate)
      ),
      platform = service<IJPlatform>()
    ).buildWithoutMLModel {

      suspendableTreeAnalysis(MLFieldCorrectElement)
      suspendableSessionAnalysis(MLFeedbackCorrectElementPosition)
      suspendableSessionAnalysis(ML_FEEDBACK_TIME_MS_TO_DISPLAY)
      suspendableSessionAnalysis(ML_FEEDBACK_TIME_MS_BEFORE_CLOSED)
      runtimeSessionAnalysis(ML_LOGGING_STATE, ML_ENABLED, ML_STATUS_CORRESPONDS_TO_BUCKET)

      logger = PyCharmImportsRankingLogger

      maxAnalysisDuration = LONGEST_POPUP_LIFE_DURATION
    }
  }
}
