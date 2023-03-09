// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch.actions

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.psi.getLineCount
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.scratch.getScratchFile
import org.jetbrains.kotlin.idea.scratch.isKotlinScratch
import org.jetbrains.kotlin.idea.scratch.isKotlinWorksheet
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ScratchRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        element.containingFile.safeAs<KtFile>()?.takeIf {
            val file = it.virtualFile
            file.isKotlinWorksheet || file.isKotlinScratch || it.isScript()
        }  ?: return null

        val declaration = element.getStrictParentOfType<KtNamedDeclaration>()
        if (declaration != null && declaration !is KtParameter && declaration.nameIdentifier == element) {
            return isLastExecutedExpression(element)
        }

        element.getParentOfType<KtScriptInitializer>(true)?.body?.let { scriptInitializer ->
            return if (scriptInitializer.findDescendantOfType<LeafPsiElement>() == element) {
                isLastExecutedExpression(element)
            } else null
        }

        // Show arrow for last added empty line
        if (declaration is KtScript && element is PsiWhiteSpace) {
            getLastExecutedExpression(element)?.let { _ ->
                if (element.getLineNumber() == element.containingFile.getLineCount() ||
                    element.getLineNumber(false) == element.containingFile.getLineCount()
                ) return Info(RunScratchFromHereAction())
            }
        }
        return null
    }

    private fun isLastExecutedExpression(element: PsiElement): Info? {
        val expression = getLastExecutedExpression(element) ?: return null
        if (element.getLineNumber(true) != expression.lineStart) {
            return null
        }

        return if (PsiTreeUtil.isAncestor(expression.element, element, false)) {
            Info(RunScratchFromHereAction())
        } else null
    }

    private fun getLastExecutedExpression(element: PsiElement): ScratchExpression? {
        val scratchFile = getSingleOpenedTextEditor(element.containingFile)?.getScratchFile() ?: return null
        if (!scratchFile.options.isRepl) return null
        val replExecutor = scratchFile.replScratchExecutor ?: return null
        return replExecutor.getFirstNewExpression()
    }

    /**
     * This method returns single editor in which passed [psiFile] opened.
     * If there is no such editor or there is more than one editor, it returns `null`.
     *
     * We use [PsiDocumentManager.getCachedDocument] instead of [PsiDocumentManager.getDocument]
     * so this would not require read action.
     */
    private fun getSingleOpenedTextEditor(psiFile: PsiFile): TextEditor? {
        val document = PsiDocumentManager.getInstance(psiFile.project).getCachedDocument(psiFile) ?: return null
        val singleOpenedEditor = EditorFactory.getInstance().getEditors(document, psiFile.project).singleOrNull() ?: return null
        return TextEditorProvider.getInstance().getTextEditor(singleOpenedEditor)
    }
}
