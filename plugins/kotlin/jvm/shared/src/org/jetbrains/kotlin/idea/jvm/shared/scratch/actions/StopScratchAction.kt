// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.scratch.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle

@ApiStatus.Internal
class StopScratchAction : ScratchAction(
    KotlinJvmBundle.messagePointer("scratch.stop.button"),
    AllIcons.Actions.Suspend
) {

    override fun actionPerformed(e: AnActionEvent) {
        ScratchCompilationSupport.forceStop()
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        val scratchFile = e.currentScratchFile ?: return

        e.presentation.isEnabledAndVisible = ScratchCompilationSupport.isInProgress(scratchFile) && !scratchFile.options.isInteractiveMode
    }
}
