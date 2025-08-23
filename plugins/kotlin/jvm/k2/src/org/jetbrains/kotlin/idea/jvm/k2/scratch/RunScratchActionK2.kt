// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.task.ProjectTaskManager
import com.intellij.task.impl.ProjectTaskManagerImpl
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.ScratchAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.ScratchCompilationSupport

internal class RunScratchActionK2 : ScratchAction(
  KotlinJvmBundle.messagePointer("scratch.run.button"), AllIcons.Actions.Execute
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val scratchFile = e.currentScratchFile as? K2KotlinScratchFile ?: return
        val executor = scratchFile.executor
        val module = scratchFile.module

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

        e.presentation.isVisible = !scratchFile.options.isInteractiveMode

        e.presentation.isEnabled = !(ScratchCompilationSupport.isAnyInProgress() || scratchFile.options.isInteractiveMode)

        if (e.presentation.isEnabled) {
            e.presentation.setTextWithMnemonic(templatePresentation.textWithPossibleMnemonic)
        } else {
            e.presentation.text = KotlinJvmBundle.message("other.scratch.file.execution.is.in.progress")
        }
    }
}