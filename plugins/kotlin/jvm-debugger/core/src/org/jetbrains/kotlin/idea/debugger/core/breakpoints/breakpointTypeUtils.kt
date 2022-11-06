// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.debugger.SourcePosition
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.xdebugger.XDebuggerUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.debugger.core.findElementAtLine
import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.idea.util.findElementsOfClassInRange
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.inline.INLINE_ONLY_ANNOTATION_FQ_NAME

fun isBreakpointApplicable(file: VirtualFile, line: Int, project: Project, checker: (PsiElement) -> ApplicabilityResult): Boolean {
    val psiFile = PsiManager.getInstance(project).findFile(file)

    if (psiFile == null || psiFile.virtualFile?.fileType != KotlinFileType.INSTANCE) {
        return false
    }

    val document = FileDocumentManager.getInstance().getDocument(file) ?: return false

    return runReadAction {
        var isApplicable = false
        val checked = HashSet<PsiElement>()

        XDebuggerUtil.getInstance().iterateLine(
            project, document, line,
            fun(element: PsiElement): Boolean {
                if (element is PsiWhiteSpace || element.getParentOfType<PsiComment>(false) != null || !element.isValid) {
                    return true
                }

                val parent = getTopmostParentOnLineOrSelf(element, document, line)
                if (!checked.add(parent)) {
                    return true
                }

                val result = checker(parent)

                if (result.shouldStop && !result.isApplicable) {
                    isApplicable = false
                    return false
                }

                isApplicable = isApplicable or result.isApplicable
                return !result.shouldStop
            },
        )

        return@runReadAction isApplicable
    }
}

private fun getTopmostParentOnLineOrSelf(element: PsiElement, document: Document, line: Int): PsiElement {
    var current = element
    var parent = current.parent
    while (parent != null && parent !is PsiFile) {
        val offset = parent.textOffset
        if (offset > document.textLength) break
        if (offset >= 0 && document.getLineNumber(offset) != line) break

        current = parent
        parent = current.parent
    }

    return current
}

fun getLambdasAtLineIfAny(sourcePosition: SourcePosition): List<KtFunction> {
    val file = sourcePosition.file as? KtFile ?: return emptyList()
    return getLambdasAtLineIfAny(file, sourcePosition.line)
}

inline fun <reified T : PsiElement> getElementsAtLineIfAny(file: KtFile, line: Int): List<T> {
    val lineElement = findElementAtLine(file, line) as? KtElement ?: return emptyList()

    val start = lineElement.startOffset
    var end = lineElement.endOffset
    var nextSibling = lineElement.nextSibling
    while (nextSibling != null && line == nextSibling.getLineNumber()) {
        end = nextSibling.endOffset
        nextSibling = nextSibling.nextSibling
    }

    return findElementsOfClassInRange(file, start, end, T::class.java).filterIsInstance<T>()
}

fun getLambdasAtLineIfAny(file: KtFile, line: Int): List<KtFunction> =
    getElementsAtLineIfAny<KtFunction>(file, line)
        .filter { it is KtFunctionLiteral || it.name == null }

fun KtCallableDeclaration.isInlineOnly(): Boolean {
    if (!hasModifier(KtTokens.INLINE_KEYWORD)) {
        return false
    }
    return annotationEntries.any { it.shortName == INLINE_ONLY_ANNOTATION_FQ_NAME.shortName() }
}
