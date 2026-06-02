// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.ClearScratchAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchOutputHandlerAdapter
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.JdksComboBoxAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.ModulesComboBoxAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.updateToolbar

class ScratchTopPanelK2(val scratchFile: K2KotlinScratchFile) {
    val actionsToolbar: ActionToolbar

    init {
        setupTopPanelUpdateHandlers()

        val modulesComboBoxAction = ModulesComboBoxAction(scratchFile) { _: Module? ->
            actionsToolbar.updateToolbar()
        }
        val jdksComboBoxAction = JdksComboBoxAction(scratchFile) {
            actionsToolbar.updateToolbar()
        }

        val toolbarGroup = DefaultActionGroup().apply {
            addAction(RunScratchActionK2())
            addAction(ClearScratchAction())
            addSeparator()
            addAction(modulesComboBoxAction)
            addAction(jdksComboBoxAction)
        }

        actionsToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, toolbarGroup, true)
    }

    private fun setupTopPanelUpdateHandlers() {
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