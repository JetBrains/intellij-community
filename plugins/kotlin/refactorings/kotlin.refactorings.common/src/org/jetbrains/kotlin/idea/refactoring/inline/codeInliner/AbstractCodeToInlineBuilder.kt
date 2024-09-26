// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.inline.codeInliner

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CommentHolder.CommentNode.Companion.mergeComments
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

abstract class AbstractCodeToInlineBuilder(
    project: Project,
    protected val originalDeclaration: KtDeclaration?,
    protected val fallbackToSuperCall: Boolean = false,
) {
    protected val psiFactory = KtPsiFactory(project)
    protected open fun saveComments(codeToInline: MutableCodeToInline, contextDeclaration: KtDeclaration) {
        val bodyBlockExpression = contextDeclaration.safeAs<KtDeclarationWithBody>()?.bodyBlockExpression
        if (bodyBlockExpression != null) addCommentHoldersForStatements(codeToInline, bodyBlockExpression)
    }

    private fun addCommentHoldersForStatements(mutableCodeToInline: MutableCodeToInline, blockExpression: KtBlockExpression) {
        val expressions = mutableCodeToInline.expressions

        for ((indexOfIteration, commentHolder) in CommentHolder.extract(blockExpression).withIndex()) {
            if (commentHolder.isEmpty) continue

            if (expressions.isEmpty()) {
                mutableCodeToInline.addExtraComments(commentHolder)
            } else {
                val expression = expressions.elementAtOrNull(indexOfIteration)
                if (expression != null) {
                    expression.mergeComments(commentHolder)
                } else {
                    expressions.last().mergeComments(
                        CommentHolder(emptyList(), trailingComments = commentHolder.leadingComments + commentHolder.trailingComments)
                    )
                }
            }
        }
    }

    protected abstract fun prepareMutableCodeToInline(
        mainExpression: KtExpression?,
        statementsBefore: List<KtExpression>,
        reformat: Boolean,
    ): MutableCodeToInline

    fun prepareCodeToInlineWithAdvancedResolution(
        bodyOrExpression: KtExpression,
        expressionMapper: (bodyOrExpression: KtExpression) -> Pair<KtExpression?, List<KtExpression>>?,
    ): CodeToInline? {
        val (mainExpression, statementsBefore) = expressionMapper(bodyOrExpression) ?: return null
        val codeToInline = prepareMutableCodeToInline(
            mainExpression = mainExpression,
            statementsBefore = statementsBefore,
            reformat = true,
        )

        val copyOfBodyOrExpression = bodyOrExpression.copied()

        // Body's expressions to be inlined contain related comments as a user data (see CommentHolder.CommentNode.Companion.mergeComments).
        // When inlining (with untouched declaration!) is reverted and called again expressions become polluted with duplicates (^ merge!).
        // Now that we copied required data it's time to clear the storage.
        codeToInline.expressions.forEach { it.putCopyableUserData(CommentHolder.COMMENTS_TO_RESTORE_KEY, null) }

        val (resultMainExpression, resultStatementsBefore) = expressionMapper(copyOfBodyOrExpression) ?: return null
        codeToInline.mainExpression = resultMainExpression
        codeToInline.statementsBefore.clear()
        codeToInline.statementsBefore.addAll(resultStatementsBefore)

        return codeToInline.toNonMutable()
    }
}