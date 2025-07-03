// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.application
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFileAutoRunner
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.ClearScratchAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.IsMakeBeforeRunAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchOutputHandlerAdapter
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.ModulesComboBoxAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.SmallBorderCheckboxAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.updateToolbar

class ScratchTopPanelK2(val scratchFile: K2KotlinScratchFile) {
    private val moduleChooserAction: ModulesComboBoxAction = ModulesComboBoxAction(scratchFile)
    val actionsToolbar: ActionToolbar

    init {
        setupTopPanelUpdateHandlers()

        val toolbarGroup = DefaultActionGroup().apply {
            addAction(RunScratchActionK2())
            addSeparator()
            addAction(ClearScratchAction())
            addSeparator()
            addAction(moduleChooserAction)
            addAction(IsMakeBeforeRunAction(scratchFile))
            addSeparator()
            addAction(IsInteractiveCheckboxAction())
        }

        actionsToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, toolbarGroup, true)
    }

    private inner class IsInteractiveCheckboxAction : SmallBorderCheckboxAction(
      text = KotlinJvmBundle.message("scratch.is.interactive.checkbox"),
      description = KotlinJvmBundle.message("scratch.is.interactive.checkbox.description", ScratchFileAutoRunner.Companion.AUTO_RUN_DELAY_MS / 1000)
    ) {
        override fun isSelected(e: AnActionEvent): Boolean {
            return scratchFile.options.isInteractiveMode
        }

        override fun setSelected(e: AnActionEvent, isInteractiveMode: Boolean) {
            if (isInteractiveMode) {
                val project = e.project
                if (project != null) {
                    (ScratchFileAutoRunner.getInstance(project) as? ScratchFileAutoRunnerK2)?.submitRun(scratchFile)
                }
            }
            scratchFile.saveOptions { copy(isInteractiveMode = isInteractiveMode) }
        }

        override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

    private fun setupTopPanelUpdateHandlers() {
        scratchFile.addModuleListener { _, _ -> actionsToolbar.updateToolbar() }

        val toolbarHandler = createUpdateToolbarHandler()
        scratchFile.executor.addOutputHandler(toolbarHandler)
    }

    private fun createUpdateToolbarHandler(): ScratchOutputHandlerAdapter {
        return object : ScratchOutputHandlerAdapter() {
            override fun onStart(file: ScratchFile) {
                actionsToolbar.updateToolbar()
            }

            override fun onFinish(file: ScratchFile) {
                actionsToolbar.updateToolbar()
            }
        }
    }
}