// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.fe10.core

import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.TextWithImports
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.debugger.core.KotlinEditorTextProvider
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils

internal object LegacyKotlinEditorTextProvider : KotlinEditorTextProvider {
    override fun getEditorText(elementAtCaret: PsiElement): TextWithImports? {
        val expression = findExpressionInner(elementAtCaret, true) ?: return null

        val expressionText = getElementInfo(expression) { it.text }
        return TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expressionText, "", KotlinFileType.INSTANCE)
    }

    override fun findExpression(elementAtCaret: PsiElement, allowMethodCalls: Boolean): Pair<PsiElement, TextRange>? {
        val expression = findExpressionInner(elementAtCaret, allowMethodCalls) ?: return null

        val expressionRange = getElementInfo(expression) { it.textRange }
        return Pair(expression, expressionRange)
    }

    override fun findEvaluationTarget(originalElement: PsiElement, allowMethodCalls: Boolean): PsiElement? {
        return findExpressionInner(originalElement, allowMethodCalls)
    }

    private fun <T> getElementInfo(expr: KtExpression, f: (PsiElement) -> T): T {
        var expressionText = f(expr)

        val nameIdentifier = expr.getNameIdentifier()
        if (nameIdentifier != null) {
            expressionText = f(nameIdentifier)
        }

        return expressionText
    }

    private fun findExpressionInner(element: PsiElement, allowMethodCalls: Boolean): KtExpression? {
        if (!isAcceptedAsCodeFragmentContext(element)) return null

        val ktElement = PsiTreeUtil.getParentOfType(element, KtElement::class.java) ?: return null

        val nameIdentifier = ktElement.getNameIdentifier()
        if (nameIdentifier == element) {
            return ktElement as? KtExpression
        }

        fun KtExpression.qualifiedParentOrSelf(isSelector: Boolean = true): KtExpression {
            val parent = parent
            return if (parent is KtQualifiedExpression && (!isSelector || parent.selectorExpression == this)) parent else this
        }

        val newExpression = when (val parent = ktElement.parent) {
            is KtThisExpression -> parent
            is KtSuperExpression -> parent.qualifiedParentOrSelf(isSelector = false)
            is KtArrayAccessExpression -> if (parent.arrayExpression == ktElement) ktElement else parent.qualifiedParentOrSelf()
            is KtReferenceExpression -> parent.qualifiedParentOrSelf()
            is KtQualifiedExpression -> if (parent.receiverExpression != ktElement) parent else null
            is KtOperationExpression -> if (parent.operationReference == ktElement) parent else null
            else -> null
        }

        if (!allowMethodCalls && newExpression != null) {
            fun PsiElement.isCall() = this is KtCallExpression || this is KtOperationExpression || this is KtArrayAccessExpression

            if (newExpression.isCall() || newExpression is KtQualifiedExpression && newExpression.selectorExpression!!.isCall()) {
                return null
            }
        }

        return when {
            newExpression is KtExpression -> newExpression
            ktElement is KtSimpleNameExpression -> {
                val context = ktElement.analyze()
                val qualifier = context[BindingContext.QUALIFIER, ktElement]
                if (qualifier != null && !DescriptorUtils.isObject(qualifier.descriptor)) {
                    null
                } else {
                    ktElement
                }
            }
            else -> null
        }

    }

    private fun KtElement.getNameIdentifier() =
        if (this is KtProperty || this is KtParameter)
            (this as PsiNameIdentifierOwner).nameIdentifier
        else
            null


    private val NOT_ACCEPTED_AS_CONTEXT_TYPES = arrayOf(
        KtUserType::class.java,
        KtImportDirective::class.java,
        KtPackageDirective::class.java,
        KtValueArgumentName::class.java
    )

    override fun isAcceptedAsCodeFragmentContext(element: PsiElement): Boolean =
        !NOT_ACCEPTED_AS_CONTEXT_TYPES.contains(element::class.java as Class<*>) &&
                PsiTreeUtil.getParentOfType(element, *NOT_ACCEPTED_AS_CONTEXT_TYPES) == null
}