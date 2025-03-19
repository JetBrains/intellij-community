// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.k2.satisfaction.survey

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ShowK2FeedbackDialogAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        K2FeedbackDialog(e.project, true).show()
    }
}