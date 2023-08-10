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

    fun takeFormattingFrom(
        element: JKFormattingOwner,
        psi: PsiElement?,
        saveLineBreaks: Boolean,
        takeCommentsBefore: Boolean,
        takeCommentsAfter: Boolean
    ) {
        if (psi == null || psi is PsiCompiledElement) return
        val (leftTokens, rightTokens) = psi.collectComments(takeCommentsBefore, takeCommentsAfter)
        element.commentsBefore += leftTokens
        element.commentsAfter += rightTokens
        if (saveLineBreaks) {
            element.hasLineBreakAfter = psi.hasLineBreakAfter()
            element.hasLineBreakBefore = psi.hasLineBreakBefore()
        }
    }

    fun takeLineBreaksFrom(element: JKFormattingOwner, psi: PsiElement?) {
        if (psi == null) return
        element.hasLineBreakAfter = psi.hasLineBreakAfter()
        element.hasLineBreakBefore = psi.hasLineBreakBefore()
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

    private fun PsiComment.indent(): String? = takeIf { parent is PsiCodeBlock }?.prevSibling?.safeAs<PsiWhiteSpace>()?.let { space ->
        val text = space.text
        if (space.prevSibling is PsiStatement)
            text.indexOfFirst(StringUtil::isLineBreak).takeIf { it != -1 }?.let { text.substring(it + 1) } ?: text
        else
            text
    }

    private fun Sequence<PsiElement>.toComments(): List<JKComment> =
        takeWhile { it is PsiComment || it is PsiWhiteSpace || it.text == ";" }
            .mapNotNull { it.asComment() }
            .toList()

    fun PsiElement.commentsAfterWithParent(): Sequence<JKComment> {
        val innerElements = commentsAfter()
        return (if (innerElements.lastOrNull()?.nextSibling == null && this is PsiKeyword)
            innerElements + parent?.commentsAfter().orEmpty()
        else innerElements).mapNotNull { it.asComment() }
    }

    private fun PsiElement.commentsBeforeWithParent(): Sequence<JKComment> {
        val innerElements = commentsBefore()
        return (if (innerElements.firstOrNull()?.prevSibling == null && this is PsiKeyword)
            innerElements + parent?.commentsBefore().orEmpty()
        else innerElements).mapNotNull { it.asComment() }
    }

    private fun PsiElement.isNonCodeElement() =
        this is PsiComment || this is PsiWhiteSpace || textMatches(";") || textLength == 0

    private fun PsiElement.commentsAfter() =
        generateSequence(nextSibling) { it.nextSibling }
            .takeWhile { it.isNonCodeElement() }

    private fun PsiElement.commentsBefore() =
        generateSequence(prevSibling) { it.prevSibling }
            .takeWhile { it.isNonCodeElement() }

    private fun PsiElement.hasLineBreakBefore() = commentsBefore().any { it is PsiWhiteSpace && it.textContains('\n') }
    private fun PsiElement.hasLineBreakAfter() = commentsAfter().any { it is PsiWhiteSpace && it.textContains('\n') }

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