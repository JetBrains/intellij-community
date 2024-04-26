// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding

import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.FeedbackSurveyType
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.InIdeFeedbackSurveyType

class OnboardingFeedbackSurvey : FeedbackSurvey() {

    override val feedbackSurveyType: FeedbackSurveyType<InIdeFeedbackSurveyConfig> =
        InIdeFeedbackSurveyType(OnboardingFeedbackSurveyConfig())

}