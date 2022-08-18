// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions.internal

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import org.jetbrains.kotlin.idea.configuration.ui.KotlinConfigurationCheckerService
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable

class ReactivePostOpenProjectActionsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        BackgroundTaskUtil.runUnderDisposeAwareIndicator(KotlinPluginDisposable.getInstance(project)) {
            KotlinConfigurationCheckerService.getInstance(project).performProjectPostOpenActions()
        }
    }
}
