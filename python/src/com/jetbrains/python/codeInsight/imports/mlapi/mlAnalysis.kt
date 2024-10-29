// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports.mlapi

import com.jetbrains.ml.analysis.MLTaskFeedback
import com.jetbrains.ml.analysis.MLTaskFeedbackField
import com.jetbrains.ml.analysis.MLUnitFeedbackField
import com.jetbrains.ml.logs.schema.BooleanEventField
import com.jetbrains.ml.logs.schema.EnumEventField
import com.jetbrains.ml.logs.schema.IntEventField
import com.jetbrains.ml.logs.schema.LongEventField

// suspendable analysis -- written after the imports were ranked

internal val MLFieldCorrectElement = MLUnitFeedbackField(
  unit = MLUnitImportCandidate,
  field = BooleanEventField("is_correct") { "The candidate was chosen" },
  throwOnTimeout = false
)


internal object MLFeedbackCorrectElementPosition : MLTaskFeedback() {
  val CANCELLED = BooleanEventField("selection_cancelled") { "No item has been selected" }
  val SELECTED_POSITION = IntEventField("selected_position") { "The position of the selected import statement" }
  val INVALID_SELECTED_ITEM = BooleanEventField("invalid_selected_item") { "The selected item hasn't been found in the initial list" }

  override val feedbackDeclaration = listOf(CANCELLED, SELECTED_POSITION, INVALID_SELECTED_ITEM)
}

internal val ML_FEEDBACK_TIME_MS_TO_DISPLAY = MLTaskFeedbackField(
  field = LongEventField("time_ms_before_displayed") { "Duration from the quickfix start until when the imports were displayed" },
  throwOnTimeout = true,
)

internal val ML_FEEDBACK_TIME_MS_BEFORE_CLOSED = MLTaskFeedbackField(
  field = LongEventField("time_ms_before_closed") { "Duration from the quickfix start until the pop-up was closed" },
)

// runtime analysis -- written during the imports are ranked

internal val ML_LOGGING_STATE: EnumEventField<LoggingOption> = EnumEventField.of<LoggingOption>("ml_logging_state", { "State of the ML session logging" })
internal val ML_ENABLED = BooleanEventField("ml_enabled") { "Machine Learning ranking is enabled" }
internal val ML_STATUS_CORRESPONDS_TO_BUCKET = BooleanEventField("ml_status_corresponds_to_bucket") { "Field 'ml_enabled' corresponds to the ML bucket % 2" }
