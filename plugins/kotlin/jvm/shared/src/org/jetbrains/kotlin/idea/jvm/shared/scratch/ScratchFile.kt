// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.scratch

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.messages.Topic
import org.jetbrains.kotlin.idea.base.psi.getTopmostElementAtOffset
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ui.ScratchFileOptionsFile
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

abstract class ScratchFile(val project: Project, val file: VirtualFile) {
    private val moduleListeners: MutableList<() -> Unit> = mutableListOf()
    var module: Module? = null
        private set

    fun getPsiFile(): PsiFile? = runReadAction {
        file.toPsiFile(project)
    }

    val ktFile: KtFile?
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

    fun getExpressions(): List<ScratchExpression> = runReadAction {
        getPsiFile()?.let { getExpressions(it) } ?: emptyList()
    }

    fun getExpressionAtLine(line: Int): ScratchExpression? = getExpressions().find { line in it.lineStart..it.lineEnd }

    fun getExpressions(psiFile: PsiFile): List<ScratchExpression> {
        // todo multiple expressions at one line
        val doc = PsiDocumentManager.getInstance(psiFile.project).getLastCommittedDocument(psiFile) ?: return emptyList()
        var line = 0
        val result = arrayListOf<ScratchExpression>()
        while (line < doc.lineCount) {
            var start = doc.getLineStartOffset(line)
            var element = psiFile.findElementAt(start)
            if (element is PsiWhiteSpace || element is PsiComment) {
                start = PsiTreeUtil.skipSiblingsForward(
                    element,
                    PsiWhiteSpace::class.java,
                    PsiComment::class.java
                )?.startOffset ?: start
                element = psiFile.findElementAt(start)
            }

            element = element?.let {
                getTopmostElementAtOffset(
                    it,
                    start,
                    KtImportDirective::class.java,
                    KtDeclaration::class.java
                )
            }

            if (element == null) {
                line++
                continue
            }

            val scratchExpression = ScratchExpression(
                element,
                doc.getLineNumber(element.startOffset),
                doc.getLineNumber(element.endOffset)
            )
            result.add(scratchExpression)

            line = scratchExpression.lineEnd + 1
        }

        return result
    }
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
        @Topic.ProjectLevel
        val TOPIC: Topic<ScratchFileListener> = Topic.create(
            "ScratchFileListener",
            ScratchFileListener::class.java
        )
    }
}