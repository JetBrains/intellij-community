// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.console

import com.intellij.execution.Executor
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager

class ConsoleCompilerHelper(
    private val project: Project,
    private val module: Module,
    private val executor: Executor,
    private val contentDescriptor: RunContentDescriptor
) {

    fun moduleIsUpToDate(): Boolean {
        val compilerManager = CompilerManager.getInstance(project)
        val compilerScope = compilerManager.createModuleCompileScope(module, true)
        return compilerManager.isUpToDate(compilerScope)
    }

    fun compileModule() {
        if (RunContentManager.getInstance(project).removeRunContent(executor, contentDescriptor)) {
            ProjectTaskManager.getInstance(project).build(module).onSuccess {
                if (!module.isDisposed) {
                    KotlinConsoleKeeper.getInstance(project).run(module, previousCompilationFailed = it.hasErrors())
                }
            }
        }
    }
}