// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.shared.scratch.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.SmallBorderCheckboxAction

class IsMakeBeforeRunAction(val scratchFile: ScratchFile) : SmallBorderCheckboxAction(KotlinJvmBundle.message("scratch.make.before.run.checkbox")) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isVisible = scratchFile.module != null && !scratchFile.options.isInteractiveMode
        e.presentation.description = scratchFile.module?.let { selectedModule ->
            KotlinJvmBundle.message("scratch.make.before.run.checkbox.description", selectedModule.name)
        }
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        return scratchFile.options.isMakeBeforeRun
    }

    override fun setSelected(e: AnActionEvent, isMakeBeforeRun: Boolean) {
        scratchFile.saveOptions { copy(isMakeBeforeRun = isMakeBeforeRun) }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}