// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.debugger.SourcePosition
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import com.intellij.util.Processor
import com.intellij.xdebugger.XDebuggerUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.psi.getLineEndOffset
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.idea.debugger.core.findElementAtLine
import org.jetbrains.kotlin.idea.util.findElementsOfClassInRange
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.inline.INLINE_ONLY_ANNOTATION_FQ_NAME
import kotlin.math.max
import kotlin.math.min

fun isBreakpointApplicable(file: VirtualFile, line: Int, project: Project, checker: (PsiElement) -> ApplicabilityResult): Boolean {
    val psiFile = PsiManager.getInstance(project).findFile(file)

    if (psiFile == null || psiFile.virtualFile?.fileType != KotlinFileType.INSTANCE) {
        return false
    }

    val document = FileDocumentManager.getInstance().getDocument(file) ?: return false

    return runReadAction {
        hasExecutableCodeImpl(checker, visitElements = { processor ->
            XDebuggerUtil.getInstance().iterateLine(project, document, line, processor)
        }) {
            getTopmostParentOnLineOrSelf(it, document, line)
        }
    }
}

internal fun KtElement.hasExecutableCodeInsideOnLine(
    file: VirtualFile, line: Int, project: Project, checker: (PsiElement) -> ApplicabilityResult
): Boolean {
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return false
    return runReadAction {
        val minOffset = max(startOffset, document.getLineStartOffset(line))
        val maxOffset = min(endOffset, document.getLineEndOffset(line))

        hasExecutableCodeImpl(checker, visitElements = { processor ->
            iterateOffsetRange(project, document, minOffset, maxOffset, processor)
        }) {
            getTopmostParentWithinOffsetRangeOrSelf(it, minOffset, maxOffset)
        }
    }
}

// TODO(KTIJ-23034): move function to XDebuggerUtil in next PR. This wasn't done in current PR
//  because this it going to be cherry-picked to kt- branches, and we can't modify java debugger part.
private fun iterateOffsetRange(project: Project, document: Document, minOffset: Int, maxOffset: Int, processor: Processor<PsiElement>) {
    val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return
    var element: PsiElement?
    var offset: Int = minOffset
    while (offset < maxOffset) {
        element = file.findElementAt(offset)
        if (element != null && element.textLength > 0) {
            offset = if (!processor.process(element)) {
                return
            } else {
                element.textRange.endOffset
            }
        } else {
            offset++
        }
    }
}

private fun hasExecutableCodeImpl(
    checker: (PsiElement) -> ApplicabilityResult,
    visitElements: (Processor<PsiElement>) -> Unit,
    getParentToAnalyze: (PsiElement) -> PsiElement,
): Boolean {
    var isApplicable = false
    val checked = HashSet<PsiElement>()

    visitElements(fun(element: PsiElement): Boolean {
        if (element is PsiWhiteSpace || element.getParentOfType<PsiComment>(false) != null || !element.isValid) {
            return true
        }
        val parent = getParentToAnalyze(element)
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
    })
    return isApplicable
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

private fun getTopmostParentWithinOffsetRangeOrSelf(element: PsiElement, startOffset: Int, endOffset: Int): PsiElement {
    var node = element
    do {
        val parent = node.parent
        if (parent == null || parent.startOffset < startOffset || parent.endOffset > endOffset) {
            break
        }
        node = parent
    } while (true)

    return node
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

fun getLambdasAtLineIfAny(file: KtFile, line: Int): List<KtFunction> {
    return getElementsAtLineIfAny<KtFunction>(file, line)
        .filter { (it is KtFunctionLiteral || it.name == null) && it.getLineNumber() == line }
}

internal fun getLambdasStartingOrEndingAtLineIfAny(file: KtFile, line: Int): List<KtFunction> {
    val start = file.getLineStartOffset(line)
    val end = file.getLineEndOffset(line)
    if (start == null || end == null) {
        return emptyList()
    }
    val result = mutableSetOf<KtFunction>()

    var offset: Int = start
    while (offset <= end) {
        val element = file.findElementAt(offset)
        if (element != null) {
            val function = element.parentOfType<KtFunction>(withSelf = true)
            if (function != null) {
                result.add(function)
            }
            offset = element.endOffset
        } else {
            offset++
        }
    }
    return result.filter {
        (it is KtFunctionLiteral || it.name == null) && it.isStartingOrEndingOnLine(line) }
}

private fun KtFunction.isStartingOrEndingOnLine(line: Int): Boolean {
    return line == getLineNumber(start = true) || line == getLineNumber(start = false)
}

fun KtCallableDeclaration.isInlineOnly(): Boolean {
    if (!hasModifier(KtTokens.INLINE_KEYWORD)) {
        return false
    }
    return annotationEntries.any { it.shortName == INLINE_ONLY_ANNOTATION_FQ_NAME.shortName() }
}
