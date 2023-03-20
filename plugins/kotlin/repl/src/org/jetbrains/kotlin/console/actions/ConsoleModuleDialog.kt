// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.console.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import org.jetbrains.kotlin.KotlinIdeaReplBundle
import org.jetbrains.kotlin.console.KotlinConsoleKeeper

class ConsoleModuleDialog(private val project: Project) {
    fun showIfNeeded(dataContext: DataContext) {
        val module = getModule(dataContext)
        if (module != null) return runConsole(module)

        val modules = ModuleManager.getInstance(project).modules

        if (modules.isEmpty()) return errorNotification(project, KotlinIdeaReplBundle.message("no.modules.were.found"))
        if (modules.size == 1) return runConsole(modules.first())

        val moduleActions = modules.sortedBy { it.name }.map { createRunAction(it) }
        val moduleGroup = DefaultActionGroup(moduleActions)

        val modulePopup = JBPopupFactory.getInstance().createActionGroupPopup(
            TITLE,
            moduleGroup,
            dataContext,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            true,
            ActionPlaces.EDITOR_POPUP,
        )

        modulePopup.showCenteredInCurrentWindow(project)
    }

    private fun getModule(dataContext: DataContext): Module? {
        val file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext) ?: return null

        val moduleForFile = ModuleUtilCore.findModuleForFile(file, project)
        if (moduleForFile != null) return moduleForFile

        return null
    }

    private fun runConsole(module: Module) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, KotlinIdeaReplBundle.message("progress.starting.repl")) {
            override fun run(indicator: ProgressIndicator) {
                KotlinConsoleKeeper.getInstance(project).run(module)
            }
        })
    }

    private fun createRunAction(module: Module) = object : AnAction(module.name) {
        override fun actionPerformed(e: AnActionEvent) = runConsole(module)
    }

    companion object {
        private val TITLE get() = KotlinIdeaReplBundle.message("choose.context.module")
    }
}