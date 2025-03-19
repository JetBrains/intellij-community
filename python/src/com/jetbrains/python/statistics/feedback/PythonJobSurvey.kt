// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.statistics.feedback

import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.InIdeFeedbackSurveyType

class PythonJobSurvey : FeedbackSurvey() {
  override val feedbackSurveyType: InIdeFeedbackSurveyType<InIdeFeedbackSurveyConfig> =
    InIdeFeedbackSurveyType(PythonJobSurveyConfig())
}