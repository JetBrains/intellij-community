// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.ShortenOptions
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.defaultValue
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.codeinsight.utils.RemoveExplicitTypeArgumentsUtils
import org.jetbrains.kotlin.idea.k2.refactoring.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.k2.refactoring.inline.KotlinInlineAnonymousFunctionProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher.isSemanticMatch
import org.jetbrains.kotlin.idea.k2.refactoring.util.AnonymousFunctionToLambdaUtil
import org.jetbrains.kotlin.idea.k2.refactoring.util.areTypeArgumentsRedundant
import org.jetbrains.kotlin.idea.k2.refactoring.util.isRedundantUnit
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.AbstractInlinePostProcessor
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.DEFAULT_PARAMETER_VALUE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.MAKE_ARGUMENT_NAMED_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.USER_CODE_KEY
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.kotlin.resolve.ArrayFqNames.ARRAY_CALL_FQ_NAMES
import org.jetbrains.kotlin.utils.addIfNotNull

object InlinePostProcessor: AbstractInlinePostProcessor() {
    override fun canMoveLambdaOutsideParentheses(expr: KtCallExpression): Boolean {
        return expr.canMoveLambdaOutsideParentheses(skipComplexCalls = false)
    }

    override fun removeRedundantUnitExpressions(pointer: SmartPsiElementPointer<KtElement>) {
        pointer.element?.forEachDescendantOfType<KtReferenceExpression> {
            if (isRedundantUnit(it)) {
                it.delete()
            }
        }
    }

    override fun removeRedundantLambdasAndAnonymousFunctions(pointer: SmartPsiElementPointer<KtElement>) {
        val element = pointer.element ?: return
        for (function in element.collectDescendantsOfType<KtFunction>().asReversed()) {
            if (function.hasBody()) {
                val call = KotlinInlineAnonymousFunctionProcessor.findCallExpression(function)
                if (call != null) {
                    KotlinInlineAnonymousFunctionProcessor.performRefactoring(call, editor = null)
                }
            }
        }
    }

    override fun shortenReferences(pointers: List<SmartPsiElementPointer<KtElement>>): List<KtElement> {
        val facility = ShortenReferencesFacility.getInstance()
        return pointers.mapNotNull { p ->
            val ktElement = p.element ?: return@mapNotNull null
            val shorten = facility.shorten(ktElement, ShortenOptions.ALL_ENABLED)
            p.element ?: shorten as? KtElement
        }
    }

    override fun simplifySpreadArrayOfArguments(pointer: SmartPsiElementPointer<KtElement>) {
        val result = pointer.element ?: return
        val argumentsToExpand = ArrayList<Pair<KtValueArgument, KtCallExpression>>()

        result.forEachDescendantOfType<KtValueArgument>(canGoInside = { it.getCopyableUserData(USER_CODE_KEY) == null }) { argument ->
            if (argument.getSpreadElement() != null && !argument.isNamed()) {
                val argumentExpression = argument.getArgumentExpression() ?: return@forEachDescendantOfType
                val callExpression =
                  ((argumentExpression as? KtDotQualifiedExpression)?.selectorExpression ?: argumentExpression) as? KtCallExpression
                        ?: return@forEachDescendantOfType
                val resolved =
                  callExpression.referenceExpression()?.mainReference?.resolve() as? KtNamedDeclaration ?: return@forEachDescendantOfType
                if (ARRAY_CALL_FQ_NAMES.contains(resolved.fqName)) {
                    argumentsToExpand.add(argument to callExpression)
                }
            }
        }

        for ((argument, replacements) in argumentsToExpand) {
            argument.replaceByMultiple(replacements)
        }
    }


    private fun KtValueArgument.replaceByMultiple(expr: KtCallExpression) {
        val list = parent as KtValueArgumentList
        val arguments = expr.valueArgumentList?.arguments.orEmpty()
        if (arguments.isEmpty()) {
            list.removeArgument(this)
        } else {
            var anchor = this
            for (argument in arguments) {
                anchor = list.addArgumentAfter(argument, anchor)
            }
            list.removeArgument(this)
        }
    }

    override fun removeExplicitTypeArguments(pointer: SmartPsiElementPointer<KtElement>) {
        val result = pointer.element ?: return
        for (typeArgumentList in result.collectDescendantsOfType<KtTypeArgumentList>(canGoInside = { it.getCopyableUserData(USER_CODE_KEY) == null }).asReversed()) {
            val callExpression = typeArgumentList.parent as? KtCallExpression

            if (callExpression != null &&
                RemoveExplicitTypeArgumentsUtils.isApplicableByPsi(callExpression) &&
                analyze(typeArgumentList) { areTypeArgumentsRedundant(typeArgumentList, true) }) {
                typeArgumentList.delete()
            }
        }
    }

    override fun dropArgumentsForDefaultValues(pointer: SmartPsiElementPointer<KtElement>) {
        val result = pointer.element ?: return

        val argumentsToDrop = ArrayList<KtValueArgument>()

        result.forEachDescendantOfType<KtCallElement> { callExpression ->
            analyze(callExpression) {
                val functionCall = callExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return@forEachDescendantOfType

                val arguments = functionCall.argumentMapping.entries.toList()
                val callableSymbol = functionCall.partiallyAppliedSymbol.symbol
                val valueParameters = callableSymbol.valueParameters
                var idx = arguments.size
                for ((argument, param) in arguments.asReversed()) {
                    idx--
                    val defaultValue = param.symbol.defaultValue
                        ?: callableSymbol.allOverriddenSymbols
                            .mapNotNull {
                                val params = (it as? KaFunctionSymbol)?.valueParameters
                                params?.getOrNull(idx)?.defaultValue
                            }.firstOrNull()

                    fun substituteDefaultValueWithPassedArguments(): @NlsSafe String? {
                        val key = Key<KtExpression>("SUBSTITUTION")
                        var needToSubstitute = false
                        defaultValue?.forEachDescendantOfType<KtSimpleNameExpression> {
                            val symbol = it.mainReference.resolveToSymbol()
                            if (symbol is KaValueParameterSymbol && symbol in valueParameters) {
                                it.putCopyableUserData(
                                    key,
                                    functionCall.argumentMapping.entries.firstOrNull { it.value.symbol == symbol }?.key
                                )
                                needToSubstitute = true
                            }
                        }

                        if (!needToSubstitute) return null

                        val copy = defaultValue!!.copy()

                        defaultValue.forEachDescendantOfType<KtSimpleNameExpression> {
                            it.putCopyableUserData(key, null)
                        }

                        copy.getCopyableUserData(key)?.let {
                            return it.text
                        }

                        copy.forEachDescendantOfType<KtSimpleNameExpression> { expr ->
                            val replacement = expr.getCopyableUserData(key)
                            if (replacement != null) {
                                expr.replace(replacement)
                            }
                        }

                        return copy.text
                    }

                    val substitutedValueText = substituteDefaultValueWithPassedArguments()

                    val valueArgument = argument.parent as? KtValueArgument ?: break
                    if (valueArgument.getCopyableUserData(DEFAULT_PARAMETER_VALUE_KEY) == null ||
                        defaultValue == null ||
                        !argument.isSemanticMatch(defaultValue) && (substitutedValueText == null || argument.text != substitutedValueText)) {
                        // for a named argument, we can try to drop arguments before it as well
                        if (!valueArgument.isNamed() && valueArgument !is KtLambdaArgument) break else continue
                    }

                    argumentsToDrop.add(valueArgument)
                }
            }
        }

        for (argument in argumentsToDrop) {
            val argumentList = argument.parent as KtValueArgumentList
            argumentList.removeArgument(argument)
            if (argumentList.arguments.isEmpty()) {
                val callExpression = argumentList.parent as KtCallElement
                if (callExpression.lambdaArguments.isNotEmpty()) {
                    argumentList.delete()
                }
            }
        }
    }

    override fun introduceNamedArguments(pointer: SmartPsiElementPointer<KtElement>) {
        val element = pointer.element ?: return
        val psiFactory = KtPsiFactory.contextual(element)
        val callsToProcess = LinkedHashSet<KtCallExpression>()
        element.forEachDescendantOfType<KtValueArgument> {
            if (it.getCopyableUserData(MAKE_ARGUMENT_NAMED_KEY) != null && !it.isNamed()) {
                val callExpression = (it.parent as? KtValueArgumentList)?.parent as? KtCallExpression
                callsToProcess.addIfNotNull(callExpression)
            }
        }

        val replacementMap = mutableMapOf<KtValueArgument, KtValueArgument>()
        analyze(element) {
            for (callExpression in callsToProcess) {
                val resolvedCall = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return

                val argumentsToMakeNamed = callExpression.valueArguments.dropWhile { it.getCopyableUserData(MAKE_ARGUMENT_NAMED_KEY) == null }
                for (argument in argumentsToMakeNamed) {
                    if (argument.isNamed()) continue
                    if (argument is KtLambdaArgument) continue
                    val argumentExpression = argument.getArgumentExpression() ?: continue
                    val name = resolvedCall.argumentMapping[argumentExpression]?.symbol?.name
                    //TODO: not always correct for vararg's
                    val newArgument = psiFactory.createArgument(argument.getArgumentExpression()!!, name, argument.getSpreadElement() != null)

                    if (argument.getCopyableUserData(DEFAULT_PARAMETER_VALUE_KEY) != null) {
                        newArgument.putCopyableUserData(DEFAULT_PARAMETER_VALUE_KEY, Unit)
                    }

                    replacementMap.put(argument, newArgument)
                }
            }
        }
        replacementMap.forEach { (argument, replacement) -> argument.replace(replacement) }
    }

    override fun convertFunctionToLambdaAndMoveOutsideParentheses(function: KtNamedFunction) {
        analyze(function) {
            AnonymousFunctionToLambdaUtil.prepareAnonymousFunctionToLambdaContext(function)
        }?.let { AnonymousFunctionToLambdaUtil.convertAnonymousFunctionToLambda(function, it) }
    }
}