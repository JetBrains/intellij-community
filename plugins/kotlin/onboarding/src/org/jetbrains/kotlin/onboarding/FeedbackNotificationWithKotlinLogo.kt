// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import org.jetbrains.kotlin.idea.KotlinIcons

class FeedbackNotificationWithKotlinLogo(groupId: String, @NlsSafe title: String, @NlsSafe content: String) :
    RequestFeedbackNotification(groupId, title, content) {

    init {
        super.setIcon(KotlinIcons.SMALL_LOGO)
    }
}