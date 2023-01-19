// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("JvmDebuggerBasePsiUtils")

package org.jetbrains.kotlin.idea.debugger.base.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.base.psi.getLineEndOffset
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Returns the `TextRange` of the line with a given zero-based index.
 */
fun PsiFile.getRangeOfLine(line: Int): TextRange? {
    if (line < 0) {
        return null
    }
    val startOffset = getLineStartOffset(line) ?: return null
    val endOffset = getLineEndOffset(line) ?: return null
    if (TextRange.isProperRange(startOffset, endOffset)) {
        return TextRange(startOffset, endOffset)
    }
    return null
}

inline fun <reified T : PsiElement> PsiFile.findElementsOfTypeInRange(range: TextRange): List<T> {
    return findElementsOfTypeInRange(range, T::class.java)
}

/**
 * Returns all `PsiElement` of given types containing in the `range`.
 * Partially included elements (e.g. El<range>element</range>) are also returned.
 */
fun <T : PsiElement> PsiFile.findElementsOfTypeInRange(range: TextRange, vararg classes: Class<out T>): List<T> {
    val parent = getParentOfLine(range) ?: return emptyList()
    return PsiTreeUtil.findChildrenOfAnyType(parent, false, *classes)
        .filter { el -> range.intersects(el.textRange) }
}

/**
 * Returns a `PsiElement` including the complete line range, or `null` if line is empty.
 */
private fun PsiFile.getParentOfLine(range: TextRange): PsiElement? {
    var parent = findElementAt(range.startOffset) ?: return null
    while (true) {
        if (parent.startOffset <= range.startOffset && parent.endOffset >= range.endOffset) {
            break
        }
        parent = parent.parent ?: break
    }
    return parent
}