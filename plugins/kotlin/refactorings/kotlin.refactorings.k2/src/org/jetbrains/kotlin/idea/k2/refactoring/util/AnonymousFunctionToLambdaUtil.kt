// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.util

import com.intellij.openapi.util.Key
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.expectedType
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.k2.refactoring.moveFunctionLiteralOutsideParenthesesIfPossible
import org.jetbrains.kotlin.idea.refactoring.getLastLambdaExpression
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

object AnonymousFunctionToLambdaUtil {

    fun convertAnonymousFunctionToLambda(element: KtNamedFunction, elementContext: KtExpression) {
        val commentSaver = CommentSaver(element)
        val returnSaver = ReturnSaver(element)
        val argument = element.getStrictParentOfType<KtValueArgument>()
        val callElement = argument?.getStrictParentOfType<KtCallElement>()
        val replaced = element.replaced(elementContext)
        if (callElement != null) {
            val callExpression = replaced.parents.firstIsInstance<KtCallExpression>()
            val callee = callExpression.getCalleeExpressionIfAny() as? KtNameReferenceExpression ?: return

            val labeledExpression = replaced as? KtLabeledExpression
            val returnLabel = labeledExpression?.getLabelNameAsName() ?: callee.getReferencedNameAsName()
            val lambda = (labeledExpression?.baseExpression ?: replaced) as KtLambdaExpression
            returnSaver.restore(lambda, returnLabel)
            commentSaver.restore(replaced, forceAdjustIndent = true)

            callExpression.getLastLambdaExpression()?.moveFunctionLiteralOutsideParenthesesIfPossible()
        } else {
            val labeledExpression = replaced as? KtLabeledExpression ?: return
            val lambdaExpression = labeledExpression.baseExpression as? KtLambdaExpression ?: return
            val returnLabel = labeledExpression.getLabelNameAsName() ?: return
            returnSaver.restore(lambdaExpression, returnLabel)
            commentSaver.restore(replaced, forceAdjustIndent = true)
        }
    }

    context(_: KaSession)
    fun prepareAnonymousFunctionToLambdaContext(element: KtNamedFunction): KtExpression? {
        if (element.receiverTypeReference != null && element.expectedType == null) return null
        val argument = element.getStrictParentOfType<KtValueArgument>()?.getArgumentExpression()
        val callElement = argument?.getStrictParentOfType<KtCallElement>()
        val typeParameterIndexes = if (callElement != null && callElement.typeArgumentList == null) {
            val functionalType = callElement.resolveToCall()?.singleFunctionCallOrNull()?.argumentMapping?.get(argument)?.symbol?.returnType

            val typeArguments = (functionalType as? KaClassType)?.typeArguments?.let {
                if (it.isNotEmpty()) it.dropLast(1) else it
            }.orEmpty()

            typeArguments.mapIndexedNotNull { index, typeProjection ->
                if (typeProjection.type is KaTypeParameterType) index else null
            }.toSet()
        } else {
            emptySet()
        }


        val returnSaver = ReturnSaver(element)
        val body = element.bodyExpression!!
        return KtPsiFactory(element.project).buildExpression {
            if (!returnSaver.isEmpty) {
                val returnLabels = element.bodyExpression
                    ?.collectDescendantsOfType<KtExpression>()
                    ?.mapNotNull {
                        when (it) {
                            is KtLabeledExpression -> it.getLabelName()
                            is KtCallExpression -> it.calleeExpression?.text
                            else -> null
                        }
                    }
                    .orEmpty()
                val calleeText = callElement?.calleeExpression?.text
                if (callElement == null || calleeText in returnLabels) {
                    val label = KotlinNameSuggester.suggestNameByName(calleeText ?: "block") { it !in returnLabels }
                    appendFixedText("$label@")
                }
            }
            appendFixedText("{")

            val parameters = element.valueParameters

            val needParameters = callElement == null
                    || typeParameterIndexes.isNotEmpty()
                    || parameters.count() > 1
                    || parameters.any { parameter -> ReferencesSearch.search(parameter, LocalSearchScope(body)).asIterable().any() }

            if (needParameters) {
                parameters.forEachIndexed { index, parameter ->
                    if (index > 0) {
                        appendFixedText(",")
                    }

                    appendName(parameter.nameAsSafeName)
                    val typeReference = parameter.typeReference
                    if (typeReference != null && (callElement == null || index in typeParameterIndexes)) {
                        appendFixedText(": ")
                        appendTypeReference(typeReference)
                    }
                }

                appendFixedText("->")
            }

            if (element.hasBlockBody()) {
                appendChildRange((body as KtBlockExpression).contentRange())
            } else {
                appendExpression(body)
            }

            appendFixedText("}")
        }
    }

    private class ReturnSaver(val function: KtNamedFunction) {
        companion object {
            private val RETURN_KEY = Key<Unit>("RETURN_KEY")
        }

        val isEmpty: Boolean

        init {
            var hasReturn = false
            analyze(function) {
                val functionSymbol = function.symbol
                val body = function.bodyExpression!!
                body.forEachDescendantOfType<KtReturnExpression> {
                    if (it.targetSymbol == functionSymbol) {
                        hasReturn = true
                        it.putCopyableUserData(RETURN_KEY, Unit)
                    }
                }
            }

            isEmpty = !hasReturn
        }

        private fun clear() {
            val body = function.bodyExpression!!
            body.forEachDescendantOfType<KtReturnExpression> { it.putCopyableUserData(RETURN_KEY, null) }
        }

        fun restore(lambda: KtLambdaExpression, label: Name) {
            clear()

            val psiFactory = KtPsiFactory(lambda.project)

            val lambdaBody = lambda.bodyExpression!!

            val returnToReplace = lambda.collectDescendantsOfType<KtReturnExpression> { it.getCopyableUserData(RETURN_KEY) != null }

            for (returnExpression in returnToReplace) {
                val value = returnExpression.returnedExpression
                val replaceWith = if (value != null && returnExpression.isValueOfBlock(lambdaBody)) {
                    value
                } else if (value != null) {
                    psiFactory.createExpressionByPattern("return@$0 $1", label, value)
                } else {
                    psiFactory.createExpressionByPattern("return@$0", label)
                }

                returnExpression.replace(replaceWith)

            }
        }

        private fun KtExpression.isValueOfBlock(inBlock: KtBlockExpression): Boolean = when (val parent = parent) {
            inBlock -> {
                this == inBlock.statements.last()
            }

            is KtBlockExpression -> {
                isValueOfBlock(parent) && parent.isValueOfBlock(inBlock)
            }

            is KtContainerNode -> {
                val owner = parent.parent
                if (owner is KtIfExpression) {
                    (this == owner.then || this == owner.`else`) && owner.isValueOfBlock(inBlock)
                } else
                    false
            }

            is KtWhenEntry -> {
                this == parent.expression && (parent.parent as KtWhenExpression).isValueOfBlock(inBlock)
            }
            else -> false
        }
    }
}
