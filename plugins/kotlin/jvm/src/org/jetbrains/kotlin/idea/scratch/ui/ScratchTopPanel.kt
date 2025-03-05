// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch.ui


import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.application
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.idea.scratch.ScratchFileAutoRunner
import org.jetbrains.kotlin.idea.scratch.ScratchFileAutoRunnerK2
import org.jetbrains.kotlin.idea.scratch.actions.ClearScratchAction
import org.jetbrains.kotlin.idea.scratch.actions.RunScratchAction
import org.jetbrains.kotlin.idea.scratch.actions.RunScratchActionK2
import org.jetbrains.kotlin.idea.scratch.actions.StopScratchAction
import org.jetbrains.kotlin.idea.scratch.output.ScratchOutputHandlerAdapter

class ScratchTopPanelK2(val scratchFile: ScratchFile) {
    private val moduleChooserAction: ModulesComboBoxAction = ModulesComboBoxAction(scratchFile)
    val actionsToolbar: ActionToolbar

    init {
        setupTopPanelUpdateHandlers()

        val toolbarGroup = DefaultActionGroup().apply {
            add(RunScratchActionK2())
            addSeparator()
            add(ClearScratchAction())
            addSeparator()
            add(moduleChooserAction)
            add(IsMakeBeforeRunAction(scratchFile))
            addSeparator()
            add(IsInteractiveCheckboxAction())
        }

        actionsToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, toolbarGroup, true)
    }

    private inner class IsInteractiveCheckboxAction : SmallBorderCheckboxAction(
        text = KotlinJvmBundle.message("scratch.is.interactive.checkbox"),
        description = KotlinJvmBundle.message("scratch.is.interactive.checkbox.description", ScratchFileAutoRunner.AUTO_RUN_DELAY_MS / 1000)
    ) {
        override fun isSelected(e: AnActionEvent): Boolean {
            return scratchFile.options.isInteractiveMode
        }

        override fun setSelected(e: AnActionEvent, isInteractiveMode: Boolean) {
            if (isInteractiveMode) {
                val project = e.project
                if (project != null) {
                    application.invokeLater {
                        runWithModalProgressBlocking(project, KotlinJvmBundle.message("progress.title.run.scratch")) {
                            (ScratchFileAutoRunner.getInstance(project) as? ScratchFileAutoRunnerK2)?.submitRun(scratchFile)
                        }
                    }
                }
            }
            scratchFile.saveOptions { copy(isInteractiveMode = isInteractiveMode) }
        }

        override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

    private fun setupTopPanelUpdateHandlers() {
        scratchFile.addModuleListener { _, _ -> actionsToolbar.updateToolbar() }

        val toolbarHandler = createUpdateToolbarHandler()
        scratchFile.k2ScratchExecutor?.addOutputHandler(toolbarHandler)
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

class ScratchTopPanel(val scratchFile: ScratchFile) {
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

private class IsMakeBeforeRunAction(val scratchFile: ScratchFile) : SmallBorderCheckboxAction(KotlinJvmBundle.message("scratch.make.before.run.checkbox")) {
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

private fun ActionToolbar.updateToolbar() {
    ApplicationManager.getApplication().invokeLater {
        updateActionsAsync()
    }
}