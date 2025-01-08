// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports

import com.jetbrains.ml.features.api.logs.BooleanEventField
import com.jetbrains.ml.features.api.logs.EnumEventField
import com.jetbrains.ml.features.api.logs.IntEventField
import com.jetbrains.ml.features.api.logs.LongEventField


internal object ContextAnalysis {
  val CANCELLED = BooleanEventField("selection_cancelled") { "No item has been selected" }
  val SELECTED_POSITION = IntEventField("selected_position") { "The position of the selected import statement" }
  val MODEL_UNAVAILABLE = BooleanEventField("model_unavailable") { "ML model was unavailable" }
  val SELECTED_POSITION_INITIAL = IntEventField("selected_position_initial") { "The position of the selected import statement, if the final ML ranking would not have happened" }
  val TIME_MS_TO_DISPLAY = LongEventField("time_ms_before_displayed") { "Duration from the quickfix start until when the imports were displayed" }
  val TIME_MS_BEFORE_CLOSED = LongEventField("time_ms_before_closed") { "Duration from the quickfix start until the pop-up was closed" }
  val ML_LOGGING_STATE = EnumEventField.of<LoggingOption>("ml_logging_state", { "State of the ML session logging" })
  val ML_ENABLED = BooleanEventField("ml_enabled") { "Machine Learning ranking is enabled" }
  val ML_STATUS_CORRESPONDS_TO_BUCKET = BooleanEventField("ml_status_corresponds_to_bucket") { "Field 'ml_enabled' corresponds to the ML bucket % 2" }
}

internal object CandidateAnalysis {
  val MLFieldCorrectElement = BooleanEventField("is_correct") { "The candidate was chosen" }
}
