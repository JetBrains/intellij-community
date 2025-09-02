// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.k1.scratch


import com.intellij.openapi.actionSystem.*
import org.jetbrains.kotlin.idea.jvm.k1.scratch.actions.RunScratchAction
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFileAutoRunner
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.ClearScratchAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.IsMakeBeforeRunAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.StopScratchAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchOutputHandlerAdapter
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.ModulesComboBoxAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.SmallBorderCheckboxAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.updateToolbar

class ScratchTopPanel(val scratchFile: K1KotlinScratchFile) {
    private val moduleChooserAction: ModulesComboBoxAction = ModulesComboBoxAction(scratchFile)
    val actionsToolbar: ActionToolbar

    init {
        setupTopPanelUpdateHandlers()

        val toolbarGroup = DefaultActionGroup().apply {
            add(RunScratchAction())
            add(StopScratchAction())
            addSeparator()
            add(ClearScratchAction())
            addSeparator()
            add(moduleChooserAction)
            add(IsMakeBeforeRunAction(scratchFile))
            addSeparator()
            add(IsInteractiveCheckboxAction())
            addSeparator()
            add(IsReplCheckboxAction())
        }

        actionsToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, toolbarGroup, true)
    }

    private fun setupTopPanelUpdateHandlers() {
        scratchFile.addModuleListener { _, _ -> actionsToolbar.updateToolbar() }

        val toolbarHandler = createUpdateToolbarHandler()
        scratchFile.replScratchExecutor?.addOutputHandler(toolbarHandler)
        scratchFile.compilingScratchExecutor?.addOutputHandler(toolbarHandler)
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

    private inner class IsInteractiveCheckboxAction : SmallBorderCheckboxAction(
        text = KotlinJvmBundle.message("scratch.is.interactive.checkbox"),
        description = KotlinJvmBundle.message("scratch.is.interactive.checkbox.description", ScratchFileAutoRunner.AUTO_RUN_DELAY_MS / 1000)
    ) {
        override fun isSelected(e: AnActionEvent): Boolean {
            return scratchFile.options.isInteractiveMode
        }

        override fun setSelected(e: AnActionEvent, isInteractiveMode: Boolean) {
            scratchFile.saveOptions { copy(isInteractiveMode = isInteractiveMode) }
        }

        override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

    private inner class IsReplCheckboxAction : SmallBorderCheckboxAction(
        text = KotlinJvmBundle.message("scratch.is.repl.checkbox"),
        description = KotlinJvmBundle.message("scratch.is.repl.checkbox.description")
    ) {
        override fun isSelected(e: AnActionEvent): Boolean {
            return scratchFile.options.isRepl
        }

        override fun setSelected(e: AnActionEvent, isRepl: Boolean) {
            scratchFile.saveOptions { copy(isRepl = isRepl) }

            if (isRepl) {
                // TODO start REPL process when checkbox is selected to speed up execution
                // Now it is switched off due to KT-18355: REPL process is keep alive if no command is executed
                //scratchFile.replScratchExecutor?.start()
            } else {
                scratchFile.replScratchExecutor?.stop()
            }
        }

        override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }
}
