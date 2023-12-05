// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ShowKotlinOnboardingFeedbackDialogAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        OnboardingFeedbackDialog(e.project, true).show()
    }
}