// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtErrorCallInfo
import org.jetbrains.kotlin.analysis.api.calls.KtExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.KtImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.k2.refactoring.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.refactoring.isInsideOfCallerBody
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.idea.refactoring.replaceListPsiAndKeepDelimiters
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.sure

internal class KotlinFunctionCallUsage(
    element: KtCallElement,
    private val callee: PsiElement
) : UsageInfo(element), KotlinBaseUsage {

    @OptIn(KtAllowAnalysisFromWriteAction::class, KtAllowAnalysisOnEdt::class)
    private val indexToExpMap: Map<Int, SmartPsiElementPointer<KtExpression>>? = allowAnalysisFromWriteAction {
        allowAnalysisOnEdt {
            analyze(element) {
                val ktCall = element.resolveCall()
                val functionCall = ktCall?.singleFunctionCallOrNull()
                    ?: return@allowAnalysisOnEdt null
                val partiallyAppliedSymbol = functionCall.partiallyAppliedSymbol
                if (ktCall is KtErrorCallInfo && partiallyAppliedSymbol.signature.valueParameters.size != element.valueArguments.size) {
                    //don't update broken call sites e.g. if new parameter is added as follows
                    //first add new argument to all function usages and only then call refactoring to update function hierarchy
                    return@allowAnalysisOnEdt null
                }
                val receiverOffset = if (callee is KtCallableDeclaration && callee.receiverTypeReference != null) 1 else 0
                val map = mutableMapOf<Int, SmartPsiElementPointer<KtExpression>>()

                val oldIdxMap: Map<KtValueParameterSymbol, Int> = partiallyAppliedSymbol.signature.valueParameters.mapIndexed { idx, s -> s.symbol to (idx + receiverOffset) }.toMap()
                functionCall.argumentMapping.forEach { (expr, variableSymbol) ->
                    map[oldIdxMap[variableSymbol.symbol]!!] = expr.createSmartPointer()
                }
                if (receiverOffset > 0) {
                    val receiver = ((partiallyAppliedSymbol.extensionReceiver ?: partiallyAppliedSymbol.dispatchReceiver) as? KtExplicitReceiverValue)?.expression
                    if (receiver != null) {
                        map[0] = receiver.createSmartPointer()
                    }
                }
                return@allowAnalysisOnEdt map
            }
        }
    }

    @OptIn(KtAllowAnalysisFromWriteAction::class, KtAllowAnalysisOnEdt::class)
    private val extensionReceiver: String? =
        allowAnalysisFromWriteAction {
            allowAnalysisOnEdt {
                analyze(element) {
                    val partiallyAppliedSymbol = element.resolveCall()?.singleFunctionCallOrNull()?.partiallyAppliedSymbol
                    when (val receiver = partiallyAppliedSymbol?.extensionReceiver) {
                        is KtExplicitReceiverValue -> receiver.expression.text
                        is KtImplicitReceiverValue -> {
                            val symbol = receiver.symbol
                            val thisText = if (symbol is KtClassifierSymbol && symbol !is KtAnonymousObjectSymbol) {
                                "this@" + symbol.name!!.asString()
                            } else {
                                "this"
                            }
                            thisText
                        }
                        else -> null
                    }
                }
            }
        }

    override fun processUsage(
      changeInfo: KotlinChangeInfoBase,
      element: KtElement,
      allUsages: Array<out UsageInfo>
    ): KtElement? {
        if (element !is KtCallElement) return null
        processUsageAndGetResult(changeInfo, element, allUsages)
        return null
    }

    fun processUsageAndGetResult(
        changeInfo: KotlinChangeInfoBase,
        element: KtCallElement,
        allUsages: Array<out UsageInfo>,
        skipRedundantArgumentList: Boolean = false,
    ): KtElement? {
        var result: KtElement? = element

        changeNameIfNeeded(changeInfo, element)

        if (element.valueArgumentList == null && changeInfo.isParameterSetOrOrderChanged && element.lambdaArguments.isNotEmpty()) {
            val anchor = element.typeArgumentList ?: element.calleeExpression
            if (anchor != null) {
                element.addAfter(KtPsiFactory(element.project).createCallArguments("()"), anchor)
            }
        }
        if (element.valueArgumentList != null) {
            if (changeInfo.isParameterSetOrOrderChanged) {
                result = updateArgumentsAndReceiver(changeInfo, element, allUsages, skipRedundantArgumentList)
            } else {
                changeArgumentNames(changeInfo, element)
            }
        }

        if (changeInfo.newParameters.isEmpty() && element is KtSuperTypeCallEntry) {
            val enumEntry = element.getStrictParentOfType<KtEnumEntry>()
            if (enumEntry != null && enumEntry.initializerList == element.parent) {
                val initializerList = enumEntry.initializerList
                enumEntry.deleteChildRange(enumEntry.getColon() ?: initializerList, initializerList)
            }
        }

        return result
    }

    private fun changeNameIfNeeded(changeInfo: ChangeInfo, element: KtCallElement) {
        if (!changeInfo.isNameChanged) return

        val callee = element.calleeExpression as? KtSimpleNameExpression ?: return

        callee.replace(KtPsiFactory(project).createSimpleName(changeInfo.newName))
    }

    class ArgumentInfo(
        val parameter: KotlinParameterInfo,
        val parameterIndex: Int,
        val resolvedArgument: SmartPsiElementPointer<KtExpression>?,
        val receiverValue: String?
    ) {
        val wasNamed: Boolean = (resolvedArgument?.element?.parent as? KtValueArgument)?.isNamed() == true

        var name: String? = null
            private set

        fun makeNamed() {
            name = parameter.getInheritedName(null)//todo
        }

        fun shouldSkip(): Boolean {
            val defaultValue = parameter.defaultValue
            return defaultValue != null && resolvedArgument?.let { defaultValue.text == it.element?.text } != false //&& mainValueArgument == null
        }
    }

    private fun ArgumentInfo.getArgumentByDefaultValue(
        element: KtCallElement,
        allUsages: Array<out UsageInfo>,
        psiFactory: KtPsiFactory
    ): KtValueArgument {
        val isInsideOfCallerBody = element.isInsideOfCallerBody(allUsages) { false } //todo
        val defaultValueForCall = parameter.defaultValueForCall
        val argValue = when {
            isInsideOfCallerBody -> psiFactory.createExpression(parameter.name)
            defaultValueForCall != null -> defaultValueForCall

            else -> null
        }

        val argName = (if (isInsideOfCallerBody) null else name)?.let { Name.identifier(it) }
        return psiFactory.createArgument(argValue ?: psiFactory.createExpression("0"), argName).apply {
            if (argValue == null) {
                getArgumentExpression()?.delete()
            }
        }
    }

    @OptIn(KtAllowAnalysisOnEdt::class, KtAllowAnalysisFromWriteAction::class)//under potemkin progress
    private fun updateArgumentsAndReceiver(
        changeInfo: KotlinChangeInfoBase,
        element: KtCallElement,
        allUsages: Array<out UsageInfo>,
        skipRedundantArgumentList: Boolean,
    ): KtElement? {
        val argumentMapping = indexToExpMap
        if (argumentMapping == null) return null
        val fullCallElement = element.getQualifiedExpressionForSelector() ?: element

        val oldArguments = element.valueArguments
        val newParameters = changeInfo.newParameters.filter { changeInfo.receiverParameterInfo != it }

        val purelyNamedCall = element is KtCallExpression && oldArguments.isNotEmpty() && oldArguments.all { it.isNamed() }

        val newReceiverInfo = changeInfo.receiverParameterInfo
        val originalReceiverInfo = changeInfo.oldReceiverInfo

        val newArgumentInfos = newParameters.asSequence().withIndex().map {
            val (index, param) = it
            val oldIndex = param.oldIndex
            val resolvedArgument = argumentMapping[oldIndex]
            val receiverValue = if (oldIndex == originalReceiverInfo?.oldIndex) extensionReceiver else null
            ArgumentInfo(param, index, resolvedArgument, receiverValue)
        }.toList()

        val lastParameterIndex = newParameters.lastIndex
        val canMixArguments = element.languageVersionSettings.supportsFeature(LanguageFeature.MixedNamedArgumentsInTheirOwnPosition)
        var firstNamedIndex = newArgumentInfos.firstOrNull {
            !canMixArguments && it.wasNamed ||
                    it.parameter.isNewParameter && it.parameter.defaultValue != null ||
                    it.resolvedArgument is KtValueArgument && it.parameterIndex < lastParameterIndex //todo varargs
        }?.parameterIndex

        if (firstNamedIndex == null) {
            val lastNonDefaultArgIndex = (lastParameterIndex downTo 0).firstOrNull { !newArgumentInfos[it].shouldSkip() } ?: -1
            firstNamedIndex = (0..lastNonDefaultArgIndex).firstOrNull { newArgumentInfos[it].shouldSkip() }
        }

        val lastPositionalIndex = if (firstNamedIndex != null) firstNamedIndex - 1 else lastParameterIndex
        val namedRange = lastPositionalIndex + 1..lastParameterIndex
        for ((index, argument) in newArgumentInfos.withIndex()) {
            if (purelyNamedCall || argument.wasNamed || index in namedRange) {
                argument.makeNamed()
            }
        }

        val psiFactory = KtPsiFactory(element.project)

        val newArgumentList = psiFactory.createCallArguments("()").apply {
            for (argInfo in newArgumentInfos) {
                if (argInfo.shouldSkip()) continue

                val name = argInfo.name?.let { Name.identifier(it) }

                if (argInfo.receiverValue != null) {
                    addArgument(psiFactory.createArgument(psiFactory.createExpression(argInfo.receiverValue), name))
                    continue
                }

                when (val resolvedArgument = argInfo.resolvedArgument) {
                    null -> {
                        val argument = argInfo.getArgumentByDefaultValue(element, allUsages, psiFactory)
                        addArgument(argument)
                    }
                    // TODO: Support Kotlin varargs

                    else -> {
                        val expression = resolvedArgument.element ?: continue
                        var newArgument: KtValueArgument = expression.parent as KtValueArgument
                        if (newArgument.getArgumentName()?.asName != name || newArgument is KtLambdaArgument) {
                            newArgument = psiFactory.createArgument(newArgument.getArgumentExpression(), name)
                        }
                        addArgument(newArgument)
                    }
                }
            }
        }
        val receiver: PsiElement?  =
            if (newReceiverInfo?.oldIndex != originalReceiverInfo?.oldIndex && newReceiverInfo != null) {
                val receiverArgument = argumentMapping[newReceiverInfo.oldIndex]?.element
                val defaultValueForCall = newReceiverInfo.defaultValueForCall
                receiverArgument?.let { psiFactory.createExpression(it.text) }
                    ?: defaultValueForCall
                    ?: psiFactory.createExpression("_")
            } else {
                null
            }

        newArgumentList.arguments.singleOrNull()?.let {
            if (it.getArgumentExpression() == null) {
                newArgumentList.removeArgument(it)
            }
        }

        val lastOldArgument = oldArguments.lastOrNull()
        val lastNewParameter = newParameters.lastOrNull()
        val oldLastResolvedArgument = argumentMapping[lastNewParameter?.oldIndex ?: -1]?.element
        val lambdaArgumentNotTouched = lastOldArgument is KtLambdaArgument && oldLastResolvedArgument == lastOldArgument

        if (lambdaArgumentNotTouched) {
            newArgumentList.removeArgument(newArgumentList.arguments.last())
        } else {
            val lambdaArguments = element.lambdaArguments
            if (lambdaArguments.isNotEmpty()) {
                element.deleteChildRange(lambdaArguments.first(), lambdaArguments.last())
            }
        }

        val oldArgumentList = element.valueArgumentList.sure { "Argument list is expected: " + element.text }
        for (argument in replaceListPsiAndKeepDelimiters(changeInfo, oldArgumentList, newArgumentList) { arguments }.arguments) {
            if (argument.getArgumentExpression() == null) argument.delete()
        }

        var newElement: KtElement = element
        if (newReceiverInfo?.oldIndex != originalReceiverInfo?.oldIndex) {
            val replacingElement: PsiElement = if (newReceiverInfo != null) {
                psiFactory.createExpressionByPattern("$0.$1", receiver!!, element)
            } else {
                element.copy()
            }

            newElement = fullCallElement.replace(replacingElement) as KtElement
        }

        val newCallExpression = newElement.safeAs<KtExpression>()?.getPossiblyQualifiedCallExpression()
        if (newCallExpression != null && allowAnalysisFromWriteAction { allowAnalysisOnEdt { newCallExpression.canMoveLambdaOutsideParentheses(false) } }) {
            newCallExpression.moveFunctionLiteralOutsideParentheses()
        }

        //if (!skipRedundantArgumentList) {
        //    newCallExpression?.valueArgumentList?.let(::removeEmptyArgumentListIfApplicable)
        //}
        //
        return newElement
    }

    private fun changeArgumentNames(changeInfo: KotlinChangeInfoBase, element: KtCallElement) {
        for (argument in element.valueArguments) {
            val argumentName = argument.getArgumentName()
            val argumentNameExpression = argumentName?.referenceExpression ?: continue
            val referencedName = argumentNameExpression.getReferencedName()
            val oldParameterIndex = changeInfo.getOldParameterIndex(referencedName) ?: continue
            val parameterInfo = changeInfo.newParameters.find { it.oldIndex == oldParameterIndex } ?: continue
            val identifier = argumentNameExpression.getIdentifier() ?: continue
            val newName = if (callee is KtCallableDeclaration) parameterInfo.getInheritedName(callee) else parameterInfo.name
            identifier.replace(KtPsiFactory(project).createIdentifier(newName))
        }
    }
}
