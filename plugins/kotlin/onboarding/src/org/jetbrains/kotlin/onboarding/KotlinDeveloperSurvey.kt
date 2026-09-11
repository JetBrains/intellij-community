// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding

import com.intellij.platform.feedback.ExternalFeedbackSurveyType
import com.intellij.platform.feedback.FeedbackSurvey
import com.intellij.platform.feedback.FeedbackSurveyType

/**
 * Promotes the annual Kotlin developer survey through the idle feedback notification.
 */
internal class KotlinDeveloperSurvey : FeedbackSurvey() {

    override val feedbackSurveyType: FeedbackSurveyType<*> =
        ExternalFeedbackSurveyType(KotlinDeveloperSurveyConfig())
}
