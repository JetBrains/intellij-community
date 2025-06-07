// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtFile

data class TargetData(
    val file: KtFile,
    val bounds: RangeMarker,
    val document: Document,
)

/**
 * Prepares the J2K target data for a given editor context.
 * Normally, the target file is the Kotlin file into which the Java code is pasted,
 * but it also supports injected documents (for example, in Kotlin Notebook cells).
 */
internal fun getTargetData(
    project: Project,
    topLevelDocument: Document,
    caretOffset: Int,
    topLevelBounds: RangeMarker
): TargetData? {
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    val topLevelPsiFile = psiDocumentManager.getPsiFile(topLevelDocument) ?: return null
    var targetBounds = topLevelBounds
    var targetDocument = topLevelDocument

    val ktFile: KtFile? = topLevelPsiFile as? KtFile ?: run {
        val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
        val injectedRange = TextRange(caretOffset, caretOffset + 1)
        val injectedDocuments = injectedLanguageManager.getCachedInjectedDocumentsInRange(topLevelPsiFile, injectedRange)
        val injectedDocument = injectedDocuments.firstOrNull() ?: return@run null
        val injectedPsiFile = psiDocumentManager.getPsiFile(injectedDocument) as? KtFile ?: return@run null

        targetBounds = injectedDocument.createRangeMarker(
            TextRange(
                injectedDocument.hostToInjected(topLevelBounds.startOffset),
                injectedDocument.hostToInjected(topLevelBounds.endOffset)
            )
        )
        targetDocument = injectedDocument

        injectedPsiFile
    }

    val targetFile = ktFile?.takeIf { it.virtualFile.isWritable } ?: return null
    return TargetData(targetFile, targetBounds, targetDocument)
}
