// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports

import com.intellij.platform.ml.logs.IJFeatureDeclarations
import com.jetbrains.mlapi.feature.FeatureContainer
import com.jetbrains.mlapi.feature.FeatureDeclaration


internal object ContextAnalysis : FeatureContainer {
  val CANCELLED = FeatureDeclaration.boolean("selection_cancelled") { "No item has been selected" }
  val SELECTED_POSITION = FeatureDeclaration.int("selected_position") { "The position of the selected import statement" }
  val MODEL_UNAVAILABLE = FeatureDeclaration.boolean("model_unavailable") { "ML model was unavailable" }
  val SELECTED_POSITION_INITIAL = FeatureDeclaration.int("selected_position_initial") { "The position of the selected import statement, if the final ML ranking would not have happened" }
  val TIME_MS_TO_DISPLAY = FeatureDeclaration.long("time_ms_before_displayed") { "Duration from the quickfix start until when the imports were displayed" }
  val TIME_MS_BEFORE_CLOSED = FeatureDeclaration.long("time_ms_before_closed") { "Duration from the quickfix start until the pop-up was closed" }
  val ML_LOGGING_STATE = IJFeatureDeclarations.enum<LoggingOption>("ml_logging_state", lazyDescription =  { "State of the ML session logging" })
  val ML_ENABLED = FeatureDeclaration.boolean("ml_enabled") { "Machine Learning ranking is enabled" }
  val REGISTRY_OPTION = IJFeatureDeclarations.enum<FinalImportRankingStatusService.RegistryOption>("registry_option", lazyDescription = { "Registry option of the experiment status" })
}

internal object CandidateAnalysis : FeatureContainer {
  val MLFieldCorrectElement = FeatureDeclaration.boolean("is_correct") { "The candidate was chosen" }
}
