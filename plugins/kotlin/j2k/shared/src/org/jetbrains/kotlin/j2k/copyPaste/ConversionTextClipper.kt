// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.siblings

/**
 * Performs some PSI surgery on the Java file that contains the to-be-converted code snippet
 * to make it more suitable for the J2K converter.
 *
 * If the user selects some part of a big `PsiElement` (like `PsiClass` without its Javadoc),
 * the J2K wants to convert the (partial) `PsiClass`, not a dozen of its children individually.
 */
internal object ConversionTextClipper {
    fun clipTextIfNeeded(file: PsiJavaFile, fileText: String, startOffsets: IntArray, endOffsets: IntArray): String? {
        val ranges = startOffsets.indices.map { TextRange(startOffsets[it], endOffsets[it]) }
            .sortedBy { it.startOffset }

        fun canDropRange(range: TextRange): Boolean = ranges.all { range !in it }

        val rangesToDrop = ArrayList<TextRange>()
        for (range in ranges) {
            val start = range.startOffset
            val end = range.endOffset
            if (start == end) continue

            val startToken = file.findElementAt(start)!!
            val elementToClipLeft = startToken.maximalParentToClip(range)
            if (elementToClipLeft != null) {
                val elementStart = elementToClipLeft.textRange.startOffset
                if (elementStart < start) {
                    val clipBound = tryClipLeftSide(elementToClipLeft, start)
                    if (clipBound != null) {
                        val rangeToDrop = TextRange(elementStart, clipBound)
                        if (canDropRange(rangeToDrop)) {
                            rangesToDrop.add(rangeToDrop)
                        }
                    }
                }
            }

            val endToken = file.findElementAt(end - 1)!!
            val elementToClipRight = endToken.maximalParentToClip(range)
            if (elementToClipRight != null) {
                val elementEnd = elementToClipRight.textRange.endOffset
                if (elementEnd > end) {
                    val clipBound = tryClipRightSide(elementToClipRight, end)
                    if (clipBound != null) {
                        val rangeToDrop = TextRange(clipBound, elementEnd)
                        if (canDropRange(rangeToDrop)) {
                            rangesToDrop.add(rangeToDrop)
                        }
                    }
                }
            }
        }

        if (rangesToDrop.isEmpty()) return null

        val newFileText = buildString {
            var offset = 0
            for (range in rangesToDrop) {
                assert(range.startOffset >= offset)
                append(fileText.substring(offset, range.startOffset))
                offset = range.endOffset
            }
            append(fileText.substring(offset, fileText.length))
        }

        fun IntArray.update() {
            for (range in rangesToDrop.asReversed()) {
                for (i in indices) {
                    val offset = this[i]
                    if (offset >= range.endOffset) {
                        this[i] = offset - range.length
                    } else {
                        assert(offset <= range.startOffset)
                    }
                }
            }
        }

        startOffsets.update()
        endOffsets.update()

        return newFileText
    }

    private fun PsiElement.maximalParentToClip(range: TextRange): PsiElement? {
        val firstNotInRange = parentsWithSelf
            .takeWhile { it !is PsiDirectory }
            .firstOrNull { it.textRange !in range }
            ?: return null
        return firstNotInRange.parentsWithSelf.lastOrNull { it.minimizedTextRange() in range }
    }

    private fun PsiElement.minimizedTextRange(): TextRange {
        val firstChild = firstChild?.siblings()?.firstOrNull { !canDropElementFromText(it) } ?: return textRange
        val lastChild = lastChild.siblings(forward = false).first { !canDropElementFromText(it) }
        return TextRange(firstChild.minimizedTextRange().startOffset, lastChild.minimizedTextRange().endOffset)
    }

    // element's text can be removed from file's text keeping parsing the same
    private fun canDropElementFromText(element: PsiElement): Boolean {
        return when (element) {
            is PsiWhiteSpace, is PsiComment, is PsiModifierList, is PsiAnnotation -> true
            is PsiJavaToken -> {
                when (element.tokenType) {
                    // modifiers
                    JavaTokenType.PUBLIC_KEYWORD, JavaTokenType.PROTECTED_KEYWORD, JavaTokenType.PRIVATE_KEYWORD,
                    JavaTokenType.STATIC_KEYWORD, JavaTokenType.ABSTRACT_KEYWORD, JavaTokenType.FINAL_KEYWORD,
                    JavaTokenType.NATIVE_KEYWORD, JavaTokenType.SYNCHRONIZED_KEYWORD, JavaTokenType.STRICTFP_KEYWORD,
                    JavaTokenType.TRANSIENT_KEYWORD, JavaTokenType.VOLATILE_KEYWORD, JavaTokenType.DEFAULT_KEYWORD ->
                        element.getParent() is PsiModifierList

                    JavaTokenType.SEMICOLON -> true
                    else -> false
                }
            }

            is PsiCodeBlock -> element.getParent() is PsiMethod
            else -> element.firstChild == null
        }
    }

    private fun tryClipLeftSide(element: PsiElement, leftBound: Int): Int? =
        tryClipSide(element, leftBound, { textRange }, { allChildren })

    private fun tryClipRightSide(element: PsiElement, rightBound: Int): Int? {
        fun Int.transform(): Int = Int.MAX_VALUE - this
        fun TextRange.transform(): TextRange = TextRange(endOffset.transform(), startOffset.transform())

        return tryClipSide(
            element,
            rightBound.transform(),
            { textRange.transform() },
            { lastChild.siblings(forward = false) }
        )?.transform()
    }

    private fun tryClipSide(
        element: PsiElement,
        rangeBound: Int,
        rangeFunction: PsiElement.() -> TextRange,
        childrenFunction: PsiElement.() -> Sequence<PsiElement>
    ): Int? {
        if (element.firstChild == null) return null

        val elementRange = element.rangeFunction()
        assert(elementRange.startOffset < rangeBound && rangeBound < elementRange.endOffset)

        var clipTo = elementRange.startOffset
        for (child in element.childrenFunction()) {
            val childRange = child.rangeFunction()

            if (childRange.startOffset >= rangeBound) { // we have cut enough already
                break
            } else if (childRange.endOffset <= rangeBound) { // need to drop the whole element
                if (!canDropElementFromText(child)) return null
                clipTo = childRange.endOffset
            } else { // rangeBound is inside child's range
                if (child is PsiWhiteSpace) break // no need to cut whitespace - we can leave it as is
                return tryClipSide(child, rangeBound, rangeFunction, childrenFunction)
            }
        }

        return clipTo
    }
}
