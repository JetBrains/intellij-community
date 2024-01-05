// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.introduce

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.idea.base.psi.dropCurlyBracketsIfPossible
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import kotlin.math.min

fun KtExpression.removeTemplateEntryBracesIfPossible(): KtExpression {
    val parent = parent as? KtBlockStringTemplateEntry ?: return this
    return parent.dropCurlyBracketsIfPossible().expression!!
}

fun KtExpression.mustBeParenthesizedInInitializerPosition(): Boolean {
    if (this !is KtBinaryExpression) return false

    if (left?.mustBeParenthesizedInInitializerPosition() == true) return true
    return PsiChildRange(left, operationReference).any { (it is PsiWhiteSpace) && it.textContains('\n') }
}

fun PsiElement.findExpressionByCopyableDataAndClearIt(key: Key<Boolean>): KtExpression? {
    val result = findDescendantOfType<KtExpression> { it.getCopyableUserData(key) != null } ?: return null
    result.putCopyableUserData(key, null)
    return result
}

fun PsiElement.findElementByCopyableDataAndClearIt(key: Key<Boolean>): PsiElement? {
    val result = findDescendantOfType<PsiElement> { it.getCopyableUserData(key) != null } ?: return null
    result.putCopyableUserData(key, null)
    return result
}

fun PsiElement.findExpressionsByCopyableDataAndClearIt(key: Key<Boolean>): List<KtExpression> {
    val results = collectDescendantsOfType<KtExpression> { it.getCopyableUserData(key) != null }
    results.forEach { it.putCopyableUserData(key, null) }
    return results
}

fun ExtractableSubstringInfo.replaceWith(replacement: KtExpression): KtExpression {
    return with(this) {
        val psiFactory = KtPsiFactory(replacement.project)
        val parent = startEntry.parent

        psiFactory.createStringTemplate(prefix).entries.singleOrNull()?.let { parent.addBefore(it, startEntry) }

        val refEntry = psiFactory.createBlockStringTemplateEntry(replacement)
        val addedRefEntry = parent.addBefore(refEntry, startEntry) as KtStringTemplateEntryWithExpression

        psiFactory.createStringTemplate(suffix).entries.singleOrNull()?.let { parent.addAfter(it, endEntry) }

        parent.deleteChildRange(startEntry, endEntry)

        addedRefEntry.expression!!
    }
}

fun findStringTemplateOrStringTemplateEntryExpression(file: KtFile, startOffset: Int, endOffset: Int, kind: ElementKind): KtExpression? {
    if (kind != ElementKind.EXPRESSION) return null

    val startEntry = file.findElementAt(startOffset)?.getNonStrictParentOfType<KtStringTemplateEntry>() ?: return null
    val endEntry = file.findElementAt(endOffset - 1)?.getNonStrictParentOfType<KtStringTemplateEntry>() ?: return null

    if (startEntry == endEntry && startEntry is KtStringTemplateEntryWithExpression) return startEntry.expression

    val stringTemplate = startEntry.parent as? KtStringTemplateExpression ?: return null
    if (endEntry.parent != stringTemplate) return null

    val templateOffset = stringTemplate.startOffset
    if (stringTemplate.getContentRange().equalsToRange(startOffset - templateOffset, endOffset - templateOffset)) return stringTemplate

    return null
}

fun KtExpression.getContainingLambdaOutsideParentheses(): KtLambdaArgument? {
    val parent = parent
    return when (parent) {
        is KtLambdaArgument -> parent
        is KtLabeledExpression -> parent.getContainingLambdaOutsideParentheses()
        else -> null
    }
}

fun calculateAnchorForExpressions(commonParent: PsiElement, commonContainer: PsiElement, expressions: List<KtExpression>): KtElement? {
    if (commonParent != commonContainer) {
        return commonParent.parents(withSelf = true).firstOrNull { it.parent == commonContainer } as? KtElement
    }
    val startOffset = expressions.fold(commonContainer.endOffset) { offset, expression ->
        min(offset, expression.substringContextOrThis.startOffset)
    }

    return commonContainer.allChildren.lastOrNull { it.textRange.contains(startOffset) } as? KtElement
}