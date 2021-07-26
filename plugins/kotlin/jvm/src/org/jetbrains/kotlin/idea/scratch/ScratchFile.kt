// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.messages.Topic
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.scratch.ui.ScratchFileOptionsFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

abstract class ScratchFile(val project: Project, val file: VirtualFile) {
    var replScratchExecutor: SequentialScratchExecutor? = null
    var compilingScratchExecutor: ScratchExecutor? = null

    private val moduleListeners: MutableList<() -> Unit> = mutableListOf()
    var module: Module? = null
        private set

    fun getExpressions(): List<ScratchExpression> = runReadAction {
        getPsiFile()?.let { getExpressions(it) } ?: emptyList()
    }

    fun getPsiFile(): PsiFile? = runReadAction {
        file.toPsiFile(project)
    }

    val ktScratchFile: KtFile?
        get() = getPsiFile().safeAs<KtFile>()

    fun setModule(value: Module?) {
        module = value
        moduleListeners.forEach { it() }
    }

    fun addModuleListener(f: (PsiFile, Module?) -> Unit) {
        moduleListeners.add {
            val selectedModule = module

            val psiFile = getPsiFile()
            if (psiFile != null) {
                f(psiFile, selectedModule)
            }
        }
    }

    val options: ScratchFileOptions
        get() = getPsiFile()?.virtualFile?.let {
            ScratchFileOptionsFile[project, it]
        } ?: ScratchFileOptions()

    fun saveOptions(update: ScratchFileOptions.() -> ScratchFileOptions) {
        val virtualFile = getPsiFile()?.virtualFile ?: return
        with(virtualFile) {
            val configToUpdate = ScratchFileOptionsFile[project, this] ?: ScratchFileOptions()
            ScratchFileOptionsFile[project, this] = configToUpdate.update()
        }
    }

    fun getExpressionAtLine(line: Int): ScratchExpression? {
        return getExpressions().find { line in it.lineStart..it.lineEnd }
    }

    abstract fun getExpressions(psiFile: PsiFile): List<ScratchExpression>
    @RequiresBackgroundThread
    abstract fun hasErrors(): Boolean
}

data class ScratchExpression(val element: PsiElement, val lineStart: Int, val lineEnd: Int = lineStart)

data class ScratchFileOptions(
    val isRepl: Boolean = false,
    val isMakeBeforeRun: Boolean = false,
    val isInteractiveMode: Boolean = true
)

interface ScratchFileListener {
    fun fileCreated(file: ScratchFile)

    companion object {
        val TOPIC = Topic.create(
            "ScratchFileListener",
            ScratchFileListener::class.java
        )
    }
}