// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import org.jetbrains.kotlin.idea.j2k.IdeaDocCommentConverter
import org.jetbrains.kotlin.nj2k.tree.JKComment
import org.jetbrains.kotlin.nj2k.tree.JKFormattingOwner
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class FormattingCollector {
    private val commentCache = mutableMapOf<PsiElement, JKComment>()

    fun copyFormattingFrom(
        element: JKFormattingOwner,
        psi: PsiElement?,
        copyLineBreaks: Boolean,
        copyLineBreaksBefore: Boolean,
        copyCommentsBefore: Boolean,
        copyCommentsAfter: Boolean
    ) {
        if (psi == null || psi is PsiCompiledElement) return
        val (commentsBefore, commentsAfter) = psi.collectComments(copyCommentsBefore, copyCommentsAfter)
        element.commentsBefore += commentsBefore
        element.commentsAfter += commentsAfter
        if (copyLineBreaks) copyLineBreaksFrom(element, psi, copyLineBreaksBefore)
    }

    fun copyLineBreaksFrom(element: JKFormattingOwner, psi: PsiElement?, copyLineBreaksBefore: Boolean) {
        if (psi == null) return
        if (copyLineBreaksBefore) {
            element.lineBreaksBefore = psi.lineBreaksBefore()
        }
        element.lineBreaksAfter = psi.lineBreaksAfter()
    }

    private fun PsiElement.asComment(): JKComment? {
        if (this in commentCache) return commentCache.getValue(this)
        val token = when (this) {
            is PsiDocComment -> JKComment(
                IdeaDocCommentConverter.convertDocComment(
                    this
                )
            )

            is PsiComment -> JKComment(text, indent())
            else -> null
        } ?: return null
        commentCache[this] = token
        return token
    }

    private fun PsiComment.indent(): String? {
        val prevWhitespace = this.takeIf { parent is PsiCodeBlock }?.prevSibling?.safeAs<PsiWhiteSpace>() ?: return null
        val text = prevWhitespace.text
        return if (prevWhitespace.prevSibling is PsiStatement) {
            text.trimStart { StringUtil.isLineBreak(it) }
        } else {
            text
        }
    }

    private fun Sequence<PsiElement>.toComments(): List<JKComment> =
        takeWhile { it is PsiComment || it is PsiWhiteSpace || it.text == ";" }
            .mapNotNull { it.asComment() }
            .toList()

    fun PsiElement.commentsAfterWithParent(): Sequence<JKComment> {
        val innerElements = nonCodeElementsAfter()
        return (if (innerElements.lastOrNull()?.nextSibling == null && this is PsiKeyword)
            innerElements + parent?.nonCodeElementsAfter().orEmpty()
        else innerElements).mapNotNull { it.asComment() }
    }

    private fun PsiElement.commentsBeforeWithParent(): Sequence<JKComment> {
        val innerElements = nonCodeElementsBefore()
        return (if (innerElements.firstOrNull()?.prevSibling == null && this is PsiKeyword)
            innerElements + parent?.nonCodeElementsBefore().orEmpty()
        else innerElements).mapNotNull { it.asComment() }
    }

    private fun PsiElement.isNonCodeElement(): Boolean =
        this is PsiComment || this is PsiWhiteSpace || textMatches(";") || textMatches(",") || textLength == 0

    private fun PsiElement.nonCodeElementsAfter(): Sequence<PsiElement> =
        generateSequence(nextSibling) { it.nextSibling }
            .takeWhile { it.isNonCodeElement() }

    private fun PsiElement.nonCodeElementsBefore(): Sequence<PsiElement> =
        generateSequence(prevSibling) { it.prevSibling }
            .takeWhile { it.isNonCodeElement() }

    private fun PsiElement.lineBreaksBefore(): Int =
        getMaxLineBreaksAmong(nonCodeElementsBefore())

    private fun PsiElement.lineBreaksAfter(): Int =
        getMaxLineBreaksAmong(nonCodeElementsAfter())

    private fun getMaxLineBreaksAmong(elements: Sequence<PsiElement>): Int {
        val whitespaces: List<String> = elements.filter { it is PsiWhiteSpace }.map { it.text }.toList()
        return if (whitespaces.isEmpty()) 0 else whitespaces.maxOf { StringUtil.getLineBreakCount(it) }
    }

    private fun PsiElement.collectComments(
        takeCommentsBefore: Boolean,
        takeCommentsAfter: Boolean
    ): Pair<List<JKComment>, List<JKComment>> {
        val leftInnerTokens = children.asSequence().toComments().asReversed()
        val rightInnerTokens = when {
            children.isEmpty() -> emptyList()
            else -> generateSequence(children.last()) { it.prevSibling }
                .toComments()
                .asReversed()
        }

        val leftComments = (leftInnerTokens + if (takeCommentsBefore) commentsBeforeWithParent() else emptySequence()).asReversed()
        val rightComments = rightInnerTokens + if (takeCommentsAfter) commentsAfterWithParent() else emptySequence()
        return leftComments to rightComments
    }
}