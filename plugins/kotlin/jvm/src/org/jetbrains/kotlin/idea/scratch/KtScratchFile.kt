// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.util.CodeInsightUtils
import org.jetbrains.kotlin.idea.util.runReadActionInSmartMode
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.AnalyzingUtils
import java.util.concurrent.Callable

class KtScratchFile(project: Project, file: VirtualFile) : ScratchFile(project, file) {
    override fun getExpressions(psiFile: PsiFile): List<ScratchExpression> {
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
                CodeInsightUtils.getTopmostElementAtOffset(
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

    @RequiresBackgroundThread
    override fun hasErrors(): Boolean {
        val psiFile = ktScratchFile ?: return false


        return ReadAction
            .nonBlocking(Callable {
                try {
                    AnalyzingUtils.checkForSyntacticErrors(psiFile)
                } catch (e: IllegalArgumentException) {
                    return@Callable true
                }

                return@Callable psiFile.analyzeWithContent().diagnostics.any { it.severity == Severity.ERROR }
            })
            .inSmartMode(project)
            .expireWith(KotlinPluginDisposable.getInstance(project))
            .expireWhen { project.isDisposed() }
            .executeSynchronously()
    }
}