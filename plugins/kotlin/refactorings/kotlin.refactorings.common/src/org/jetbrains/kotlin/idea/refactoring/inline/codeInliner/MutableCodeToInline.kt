// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.inline.codeInliner

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.psi.BuilderByPattern
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.ImportPath

private val POST_INSERTION_ACTION: Key<(KtElement) -> Unit> = Key("POST_INSERTION_ACTION")
private val PRE_COMMIT_ACTION: Key<(KtElement) -> Unit> = Key("PRE_COMMIT_ACTION_KEY")

class MutableCodeToInline(
    var mainExpression: KtExpression?,
    var originalDeclaration: KtDeclaration?,
    val statementsBefore: MutableList<KtExpression>,
    val fqNamesToImport: MutableCollection<ImportPath>,
    val alwaysKeepMainExpression: Boolean,
    var extraComments: CommentHolder?,
) {
    fun <TElement : KtElement> addPostInsertionAction(element: TElement, action: (TElement) -> Unit) {
        assert(element in this)
        @Suppress("UNCHECKED_CAST")
        element.putCopyableUserData(POST_INSERTION_ACTION, action as (KtElement) -> Unit)
    }

    fun <TElement : KtElement> addPreCommitAction(element: TElement, action: (TElement) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        element.putCopyableUserData(PRE_COMMIT_ACTION, action as (KtElement) -> Unit)
    }

    fun performPostInsertionActions(elements: Collection<PsiElement>) {
        for (element in elements) {
            element.forEachDescendantOfType<KtElement> { performAction(it, POST_INSERTION_ACTION) }
        }
    }

    fun addExtraComments(commentHolder: CommentHolder) {
        extraComments = extraComments?.merge(commentHolder) ?: commentHolder
    }

    fun BuilderByPattern<KtExpression>.appendExpressionsFromCodeToInline(postfixForMainExpression: String = "") {
        for (statement in statementsBefore) {
            appendExpression(statement)
            appendFixedText("\n")
        }

        if (mainExpression != null) {
            appendExpression(mainExpression)
            appendFixedText(postfixForMainExpression)
        }
    }

    fun replaceExpression(oldExpression: KtExpression, newExpression: KtExpression): KtExpression {
        assert(oldExpression in this)

        if (oldExpression == mainExpression) {
            mainExpression = newExpression
            return newExpression
        }

        val index = statementsBefore.indexOf(oldExpression)
        if (index >= 0) {
            statementsBefore[index] = newExpression
            return newExpression
        }

        return oldExpression.replace(newExpression) as KtExpression
    }

    val expressions: Collection<KtExpression>
        get() = statementsBefore + listOfNotNull(mainExpression)

    operator fun contains(element: PsiElement): Boolean = expressions.any { it.isAncestor(element) }
}

fun CodeToInline.toMutable(): MutableCodeToInline = MutableCodeToInline(
    mainExpression?.copied(),
    originalDeclaration,
    statementsBefore.asSequence().map { it.copied() }.toMutableList(),
    fqNamesToImport.toMutableSet(),
    alwaysKeepMainExpression,
    extraComments,
)

private fun performAction(element: KtElement, actionKey: Key<(KtElement) -> Unit>) {
    val action = element.getCopyableUserData(actionKey)
    if (action != null) {
        element.putCopyableUserData(actionKey, null)
        action.invoke(element)
    }
}

private fun performPreCommitActions(expressions: Collection<KtExpression>) = expressions.asSequence()
    .flatMap { it.collectDescendantsOfType<KtElement> { element -> element.getCopyableUserData(PRE_COMMIT_ACTION) != null } }
    .sortedWith(compareByDescending(KtElement::startOffset).thenBy(PsiElement::getTextLength))
    .forEach { performAction(it, PRE_COMMIT_ACTION) }

fun MutableCodeToInline.toNonMutable(): CodeToInline {
    performPreCommitActions(expressions)
    return CodeToInline(
        mainExpression,
        originalDeclaration,
        statementsBefore,
        fqNamesToImport,
        alwaysKeepMainExpression,
        extraComments
    )
}

inline fun <reified T : PsiElement> MutableCodeToInline.collectDescendantsOfType(noinline predicate: (T) -> Boolean = { true }): List<T> {
    return expressions.flatMap { it.collectDescendantsOfType({ true }, predicate) }
}

inline fun <reified T : PsiElement> MutableCodeToInline.forEachDescendantOfType(noinline action: (T) -> Unit) {
    expressions.forEach { it.forEachDescendantOfType(action) }
}

