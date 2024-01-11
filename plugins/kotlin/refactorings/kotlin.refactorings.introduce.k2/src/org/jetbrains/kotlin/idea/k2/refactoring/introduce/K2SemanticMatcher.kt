// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.calls
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.idea.base.psi.isInsideKtTypeReference
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*

object K2SemanticMatcher {
    context(KtAnalysisSession)
    fun findMatches(patternElement: KtElement, scopeElement: KtElement): List<KtElement> {
        val matches = mutableListOf<KtElement>()

        scopeElement.accept(
            object : KtTreeVisitorVoid() {
                override fun visitKtElement(element: KtElement) {
                    if (element.isSemanticMatch(patternElement)) {
                        matches.add(element)
                    } else {
                        super.visitKtElement(element)
                    }
                }
            }
        )

        return matches
    }

    context(KtAnalysisSession)
    fun KtElement.isSemanticMatch(patternElement: KtElement): Boolean = accept(VisitingMatcher(this@KtAnalysisSession), patternElement)

    private class VisitingMatcher(private val analysisSession: KtAnalysisSession) : KtVisitor<Boolean, KtElement>() {
        override fun visitKtElement(element: KtElement, data: KtElement): Boolean = false

        override fun visitDeclaration(dcl: KtDeclaration, data: KtElement): Boolean = false // TODO

        override fun visitExpression(expression: KtExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtExpression ?: return false

            if (expression.isInsideKtTypeReference != patternExpression.isInsideKtTypeReference) return false

            return when (patternExpression) {
                is KtQualifiedExpression,
                is KtReferenceExpression -> with(analysisSession) { areCallsMatchingByResolve(expression, patternExpression) }

                else -> false
            }
        }

        override fun visitLoopExpression(loopExpression: KtLoopExpression, data: KtElement): Boolean = false // TODO()

        override fun visitConstantExpression(expression: KtConstantExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtConstantExpression ?: return false

            return expression.text == patternExpression.text
        }

        override fun visitUnaryExpression(expression: KtUnaryExpression, data: KtElement): Boolean = false // TODO

        override fun visitBinaryExpression(expression: KtBinaryExpression, data: KtElement): Boolean = false // TODO

        override fun visitExpressionWithLabel(expression: KtExpressionWithLabel, data: KtElement): Boolean? {
            val targetReference = (expression as? KtInstanceExpressionWithLabel)?.mainReference ?: return false
            val patternReference = (data.deparenthesized() as? KtInstanceExpressionWithLabel)?.mainReference ?: return false

            return with(analysisSession) { areReferencesMatchingByResolve(targetReference, patternReference) }
        }

        override fun visitThrowExpression(expression: KtThrowExpression, data: KtElement): Boolean = false // TODO()

        override fun visitIfExpression(expression: KtIfExpression, data: KtElement): Boolean = false // TODO

        override fun visitWhenExpression(expression: KtWhenExpression, data: KtElement): Boolean = false // TODO

        override fun visitCollectionLiteralExpression(expression: KtCollectionLiteralExpression, data: KtElement): Boolean = false // TODO()

        override fun visitTryExpression(expression: KtTryExpression, data: KtElement): Boolean = false

        override fun visitLambdaExpression(expression: KtLambdaExpression, data: KtElement): Boolean = false

        override fun visitAnnotatedExpression(expression: KtAnnotatedExpression, data: KtElement): Boolean = false // TODO()

        override fun visitQualifiedExpression(expression: KtQualifiedExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtExpression ?: return false

            return when (patternExpression) {
                is KtQualifiedExpression,
                is KtReferenceExpression -> with(analysisSession) { areCallsMatchingByResolve(expression, patternExpression) }

                else -> false
            }
        }

        override fun visitDoubleColonExpression(expression: KtDoubleColonExpression, data: KtElement): Boolean = false // TODO()

        override fun visitObjectLiteralExpression(expression: KtObjectLiteralExpression, data: KtElement): Boolean = false // TODO()

        override fun visitBlockExpression(expression: KtBlockExpression, data: KtElement): Boolean = false

        override fun visitParenthesizedExpression(expression: KtParenthesizedExpression, data: KtElement): Boolean =
            expression.deparenthesized().accept(visitor = this, data)

        override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS, data: KtElement): Boolean = false // TODO

        override fun visitStringTemplateExpression(expression: KtStringTemplateExpression, data: KtElement): Boolean = false // TODO

        override fun visitIsExpression(expression: KtIsExpression, data: KtElement): Boolean = false // TODO
    }

    context(KtAnalysisSession)
    private fun areCallsMatchingByResolve(targetExpression: KtExpression, patternExpression: KtExpression): Boolean {
        val targetCall = targetExpression.resolveCall()?.calls?.singleOrNull() ?: return false
        val patternCall = patternExpression.resolveCall()?.calls?.singleOrNull() ?: return false

        if (targetCall is KtCallableMemberCall<*, *> && patternCall is KtCallableMemberCall<*, *>) {
            val targetPartiallyAppliedSymbol = targetCall.partiallyAppliedSymbol

            if (targetPartiallyAppliedSymbol.dispatchReceiver != null) return false // TODO
            if (targetPartiallyAppliedSymbol.extensionReceiver != null) return false // TODO

            if (targetCall.symbol != patternCall.symbol) return false

            return true
        }

        return false
    }

    context(KtAnalysisSession)
    private fun areReferencesMatchingByResolve(targetReference: KtReference, patternReference: KtReference): Boolean =
        targetReference.resolveToSymbol()?.equals(patternReference.resolveToSymbol()) == true

    private val KtInstanceExpressionWithLabel.mainReference: KtReference get() = instanceReference.mainReference

    private fun KtElement.deparenthesized(): KtElement = when (this) {
        is KtExpression -> this.safeDeparenthesize()
        else -> this
    }
}