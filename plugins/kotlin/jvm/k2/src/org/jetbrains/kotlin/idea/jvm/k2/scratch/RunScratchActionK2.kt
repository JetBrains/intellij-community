// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.task.ProjectTaskManager
import com.intellij.task.impl.ProjectTaskManagerImpl
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.ScratchAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.ScratchCompilationSupport

class RunScratchActionK2 : ScratchAction(
    KotlinJvmBundle.messagePointer("scratch.run.button"), AllIcons.Actions.Execute
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val scratchFile = e.currentScratchFile as? K2KotlinScratchFile ?: return
        val executor = scratchFile.executor
        val module = scratchFile.module

        if (scratchFile.jdk == null) {
            executor.errorOccurs(KotlinJvmBundle.message("scratch.no.jdk.selected"))
            return
        }

        ProjectTaskManagerImpl.putBuildOriginator(project, this.javaClass)

        if (module != null && scratchFile.options.isMakeBeforeRun) {
            ProjectTaskManager.getInstance(project).build(module).onSuccess { executionResult ->
                if (executionResult.isAborted || executionResult.hasErrors()) {
                    executor.errorOccurs(KotlinJvmBundle.message("there.were.compilation.errors.in.module.0", module.name))
                } else {
                    executor.execute()
                }
            }
        } else {
            executor.execute()
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        val scratchFile = e.currentScratchFile ?: return

        val hasJdk = scratchFile.jdk != null
        val isInteractiveMode = scratchFile.options.isInteractiveMode
        val isExecutionInProgress = ScratchCompilationSupport.isAnyInProgress()
        val disabledByInteractiveModeMessage = KotlinJvmBundle.message("scratch.run.disabled.interactive.description")
        e.presentation.isVisible = true
        e.presentation.isEnabled = hasJdk && !(isExecutionInProgress || isInteractiveMode)
        e.presentation.description = when {
            isInteractiveMode -> disabledByInteractiveModeMessage
            !hasJdk -> KotlinJvmBundle.message("scratch.no.jdk.selected")
            isExecutionInProgress -> KotlinJvmBundle.message("other.scratch.file.execution.is.in.progress")
            else -> templatePresentation.description
        }

        when {
            isInteractiveMode -> e.presentation.text = disabledByInteractiveModeMessage
            !hasJdk -> e.presentation.text = KotlinJvmBundle.message("scratch.no.jdk.selected.action")
            isExecutionInProgress -> e.presentation.text = KotlinJvmBundle.message("other.scratch.file.execution.is.in.progress")
            else -> e.presentation.setTextWithMnemonic(templatePresentation.textWithPossibleMnemonic)
        }
    }
}
