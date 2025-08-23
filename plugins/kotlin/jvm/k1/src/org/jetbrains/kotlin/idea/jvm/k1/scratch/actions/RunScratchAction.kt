// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.k1.scratch.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbService
import com.intellij.task.ProjectTaskManager
import com.intellij.task.impl.ProjectTaskManagerImpl
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.jvm.k1.scratch.K1KotlinScratchFile
import org.jetbrains.kotlin.idea.jvm.k1.scratch.SequentialScratchExecutor
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.ScratchAction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.ScratchCompilationSupport
import org.jetbrains.kotlin.idea.jvm.shared.scratch.printDebugMessage
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.idea.jvm.shared.scratch.LOG as log

class RunScratchAction : ScratchAction(
    KotlinJvmBundle.messagePointer("scratch.run.button"), AllIcons.Actions.Execute
) {
    init {
        KeymapManager.getInstance().activeKeymap.getShortcuts("Kotlin.RunScratch").firstOrNull()?.let {
            templatePresentation.text += " (${KeymapUtil.getShortcutText(it)})"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val scratchFile = e.currentScratchFile as? K1KotlinScratchFile ?: return
        Handler.doAction(scratchFile, false)
    }

    object Handler {
        @OptIn(UnsafeCastFunction::class)
        fun doAction(scratchFile: K1KotlinScratchFile, isAutoRun: Boolean) {
            val project = scratchFile.project
            val isRepl = scratchFile.options.isRepl
            val executor = (if (isRepl) scratchFile.replScratchExecutor else scratchFile.compilingScratchExecutor) ?: return

            log.printDebugMessage("Run Action: isRepl = $isRepl")

            fun executeScratch() {
                try {
                    if (isAutoRun && executor is SequentialScratchExecutor) {
                        executor.executeNew()
                    } else {
                        executor.execute()
                    }
                } catch (ex: Throwable) {
                    executor.errorOccurs(KotlinJvmBundle.message("exception.occurs.during.run.scratch.action"), ex, true)
                }
            }

            val isMakeBeforeRun = scratchFile.options.isMakeBeforeRun
            log.printDebugMessage("Run Action: isMakeBeforeRun = $isMakeBeforeRun")

            ScriptConfigurationManager.getInstance(project).updateScriptDependenciesIfNeeded(scratchFile.file)
            val module = scratchFile.module
            log.printDebugMessage("Run Action: module = ${module?.name}")

            if (!isAutoRun && module != null && isMakeBeforeRun) {
                ProjectTaskManagerImpl.putBuildOriginator(project, this.javaClass)
                ProjectTaskManager.getInstance(project).build(module).onSuccess { executionResult ->
                    if (executionResult.isAborted || executionResult.hasErrors()) {
                        executor.errorOccurs(KotlinJvmBundle.message("there.were.compilation.errors.in.module.0", module.name))
                    }

                    if (DumbService.isDumb(project)) {
                        DumbService.getInstance(project).smartInvokeLater {
                            executeScratch()
                        }
                    } else {
                        executeScratch()
                    }
                }
            } else {
                executeScratch()
            }
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        val scratchFile = e.currentScratchFile ?: return

        e.presentation.isEnabled = !(ScratchCompilationSupport.isAnyInProgress() || scratchFile.options.isInteractiveMode)

        if (e.presentation.isEnabled) {
            e.presentation.text = templatePresentation.text
        } else {
            e.presentation.text = KotlinJvmBundle.message("other.scratch.file.execution.is.in.progress")
        }


        e.presentation.isVisible = !(ScratchCompilationSupport.isAnyInProgress() || scratchFile.options.isInteractiveMode)
    }
}