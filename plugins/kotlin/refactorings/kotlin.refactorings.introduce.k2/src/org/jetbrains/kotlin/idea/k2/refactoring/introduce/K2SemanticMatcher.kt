// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider.getArgumentOrIndexExpressions
import org.jetbrains.kotlin.idea.base.analysis.api.utils.CallParameterInfoProvider.mapArgumentsToParameterIndices
import org.jetbrains.kotlin.idea.base.psi.isInsideKtTypeReference
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.zipWithNulls

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

    context(KtAnalysisSession)
    private fun elementsMatchOrBothAreNull(targetElement: KtElement?, patternElement: KtElement?): Boolean {
        if (targetElement == null || patternElement == null) return targetElement == null && patternElement == null
        return targetElement.isSemanticMatch(patternElement)
    }

    private class VisitingMatcher(private val analysisSession: KtAnalysisSession) : KtVisitor<Boolean, KtElement>() {
        private fun elementsMatchOrBothAreNull(targetElement: KtElement?, patternElement: KtElement?): Boolean {
            if (targetElement == null || patternElement == null) return targetElement == null && patternElement == null

            return targetElement.accept(visitor = this, patternElement)
        }

        override fun visitKtElement(element: KtElement, data: KtElement): Boolean = false

        override fun visitDeclaration(dcl: KtDeclaration, data: KtElement): Boolean = false // TODO

        override fun visitExpression(expression: KtExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtExpression ?: return false
            if (patternExpression is KtQualifiedExpression) {
                return patternExpression.selectorExpression?.let { visitExpression(expression, it) } == true
            }
            if (expression.isSafeCall() != patternExpression.isSafeCall()) return false
            if (expression.isAssignmentOperation() != patternExpression.isAssignmentOperation()) return false
            if (expression.isInsideKtTypeReference != patternExpression.isInsideKtTypeReference) return false
            if (expression is KtSimpleNameExpression != patternExpression is KtSimpleNameExpression) return false
            if (patternExpression !is KtReferenceExpression && patternExpression !is KtOperationExpression) return false

            with(analysisSession) {
                if (!areCallsMatchingByResolve(expression, patternExpression)) return false
            }
            return true
        }

        override fun visitLoopExpression(loopExpression: KtLoopExpression, data: KtElement): Boolean = false // TODO()

        override fun visitConstantExpression(expression: KtConstantExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtConstantExpression ?: return false

            return expression.text == patternExpression.text
        }

        override fun visitUnaryExpression(expression: KtUnaryExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtExpression ?: return false
            if (patternExpression is KtUnaryExpression) {
                if (expression::class != patternExpression::class) return false
                if (expression.operationToken != patternExpression.operationToken) return false
                with(analysisSession) {
                    if (!areReferencesMatchingByResolve(expression.mainReference, patternExpression.mainReference)) return false
                }
                return elementsMatchOrBothAreNull(expression.baseExpression, patternExpression.baseExpression)
            }
            return visitExpression(expression, patternExpression)
        }

        override fun visitBinaryExpression(expression: KtBinaryExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtExpression ?: return false
            if (patternExpression is KtBinaryExpression) {
                if (expression.operationToken != patternExpression.operationToken) return false
                with(analysisSession) {
                    if (!areReferencesMatchingByResolve(expression.mainReference, patternExpression.mainReference)) return false
                }
                return elementsMatchOrBothAreNull(expression.left, patternExpression.left) &&
                        elementsMatchOrBothAreNull(expression.right, patternExpression.right)
            }
            return visitExpression(expression, patternExpression)
        }

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
            val targetSelectorExpression = expression.selectorExpression ?: return false
            return visitExpression(targetSelectorExpression, data)
        }

        override fun visitDoubleColonExpression(expression: KtDoubleColonExpression, data: KtElement): Boolean = false // TODO()

        override fun visitObjectLiteralExpression(expression: KtObjectLiteralExpression, data: KtElement): Boolean = false // TODO()

        override fun visitBlockExpression(expression: KtBlockExpression, data: KtElement): Boolean = false

        override fun visitParenthesizedExpression(expression: KtParenthesizedExpression, data: KtElement): Boolean =
            expression.deparenthesized().accept(visitor = this, data)

        override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS, data: KtElement): Boolean = false // TODO

        override fun visitStringTemplateExpression(expression: KtStringTemplateExpression, data: KtElement): Boolean {
            val patternExpression = data.deparenthesized() as? KtStringTemplateExpression ?: return false

            if (expression.entries.size != patternExpression.entries.size) return false
            for ((targetEntry, patternEntry) in expression.entries.zip(patternExpression.entries)) {
                when {
                    targetEntry is KtLiteralStringTemplateEntry && patternEntry is KtLiteralStringTemplateEntry -> {
                        if (targetEntry.text != patternEntry.text) return false
                    }

                    targetEntry is KtStringTemplateEntryWithExpression && patternEntry is KtStringTemplateEntryWithExpression -> {
                        if (!elementsMatchOrBothAreNull(targetEntry.expression, patternEntry.expression)) return false
                    }

                    targetEntry is KtEscapeStringTemplateEntry && patternEntry is KtEscapeStringTemplateEntry -> {
                        if (targetEntry.unescapedValue != patternEntry.unescapedValue) return false
                    }

                    else -> return false
                }
            }

            return true
        }

        override fun visitIsExpression(expression: KtIsExpression, data: KtElement): Boolean = false // TODO
    }

    context(KtAnalysisSession)
    private fun areCallsMatchingByResolve(targetExpression: KtExpression, patternExpression: KtExpression): Boolean {
        val targetCall = targetExpression.resolveCall()?.calls?.singleOrNull() ?: return false
        val patternCall = patternExpression.resolveCall()?.calls?.singleOrNull() ?: return false

        if (targetCall.javaClass != patternCall.javaClass) return false

        if (targetCall is KtCallableMemberCall<*, *> && patternCall is KtCallableMemberCall<*, *>) {
            val targetAppliedSymbol = targetCall.partiallyAppliedSymbol
            val patternAppliedSymbol = patternCall.partiallyAppliedSymbol

            if (targetCall.symbol != patternCall.symbol) return false

            if (!areReceiversMatching(targetAppliedSymbol.dispatchReceiver, patternAppliedSymbol.dispatchReceiver)) return false
            if (!areReceiversMatching(targetAppliedSymbol.extensionReceiver, patternAppliedSymbol.extensionReceiver)) return false
        }

        val targetArguments = targetExpression.getArgumentsAndSortThemIfCallIsPresent(targetCall as? KtFunctionCall<*>)
        val patternArguments = patternExpression.getArgumentsAndSortThemIfCallIsPresent(patternCall as? KtFunctionCall<*>)

        for ((targetArgument, patternArgument) in targetArguments.zipWithNulls(patternArguments)) {
            if (!elementsMatchOrBothAreNull(targetArgument, patternArgument)) return false
        }

        return true
    }

    context(KtAnalysisSession)
    private fun KtExpression.getArgumentsAndSortThemIfCallIsPresent(call: KtFunctionCall<*>?): List<KtExpression?> {
        val allArguments = getArgumentOrIndexExpressions(sourceElement = this)

        if (call == null) return allArguments

        val signature = call.partiallyAppliedSymbol.signature
        val mappedArguments = mapArgumentsToParameterIndices(sourceElement = this, signature, call.argumentMapping)
        val sortedMappedArguments = mappedArguments.toList()
            .sortedWith(compareBy({ (_, parameterIndex) -> parameterIndex }, { (argument, _) -> argument.startOffset }))
            .map { (argument, _) -> argument }

        return sortedMappedArguments + allArguments.filterNot { it in mappedArguments }
    }

    context(KtAnalysisSession)
    private fun areReceiversMatching(targetReceiver: KtReceiverValue?, patternReceiver: KtReceiverValue?): Boolean {
        return when (targetReceiver) {
            is KtImplicitReceiverValue -> {
                when (patternReceiver) {
                    is KtImplicitReceiverValue -> targetReceiver.symbol == patternReceiver.symbol
                    is KtExplicitReceiverValue -> targetReceiver.symbol == patternReceiver.getSymbolForThisExpressionOrNull()
                    else -> false
                }
            }

            is KtSmartCastedReceiverValue -> false // TODO()

            is KtExplicitReceiverValue -> {
                when (patternReceiver) {
                    is KtImplicitReceiverValue -> targetReceiver.getSymbolForThisExpressionOrNull() == patternReceiver.symbol
                    is KtExplicitReceiverValue -> targetReceiver.expression.isSemanticMatch(patternReceiver.expression)
                    else -> false
                }
            }

            null -> patternReceiver == null
        }
    }

    context(KtAnalysisSession)
    private fun KtExplicitReceiverValue.getSymbolForThisExpressionOrNull(): KtSymbol? =
        (expression as? KtThisExpression)?.mainReference?.resolveToSymbol()

    context(KtAnalysisSession)
    private fun areReferencesMatchingByResolve(targetReference: KtReference, patternReference: KtReference): Boolean =
        targetReference.resolveToSymbol()?.equals(patternReference.resolveToSymbol()) == true

    private val KtInstanceExpressionWithLabel.mainReference: KtReference get() = instanceReference.mainReference

    private val KtOperationExpression.mainReference: KtSimpleNameReference get() = operationReference.mainReference

    private fun KtElement.deparenthesized(): KtElement = when (this) {
        is KtExpression -> this.safeDeparenthesize()
        else -> this
    }

    private fun KtExpression.isSafeCall(): Boolean = (parent as? KtSafeQualifiedExpression)?.selectorExpression == this

    private fun KtExpression.isAssignmentOperation(): Boolean =
        (this as? KtOperationReferenceExpression)?.operationSignTokenType == KtTokens.EQ
}