// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.inline.codeInliner

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.getRenderedTypeArguments
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.DEFAULT_PARAMETER_VALUE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.MAKE_ARGUMENT_NAMED_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.NEW_DECLARATION_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.PARAMETER_VALUE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.RECEIVER_VALUE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.USER_CODE_KEY
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

abstract class AbstractCodeInliner<TCallElement : KtElement, Parameter : Any, KotlinType, CallableDescriptor>(
    private val callElement: TCallElement,
    codeToInline: CodeToInline
) {
    protected val codeToInline = codeToInline.toMutable()
    protected val project = callElement.project
    protected val psiFactory = KtPsiFactory(project)

    protected fun KtElement.callableReferenceExpressionForReference(): KtCallableReferenceExpression? =
        parent.safeAs<KtCallableReferenceExpression>()?.takeIf { it.callableReference == callElement }

    protected fun KtSimpleNameExpression.receiverExpression(): KtExpression? =
        getReceiverExpression() ?: parent.safeAs<KtCallableReferenceExpression>()?.receiverExpression

    protected fun KtExpression?.shouldKeepValue(usageCount: Int): Boolean {
        if (usageCount == 1) return false
        val sideEffectOnly = usageCount == 0

        return when (this) {
            is KtSimpleNameExpression -> false
            is KtQualifiedExpression -> receiverExpression.shouldKeepValue(usageCount) || selectorExpression.shouldKeepValue(usageCount)
            is KtUnaryExpression -> operationToken in setOf(KtTokens.PLUSPLUS, KtTokens.MINUSMINUS) ||
                    baseExpression.shouldKeepValue(usageCount)

            is KtStringTemplateExpression -> entries.any {
                if (sideEffectOnly) it.expression.shouldKeepValue(usageCount) else it is KtStringTemplateEntryWithExpression
            }

            is KtThisExpression, is KtSuperExpression, is KtConstantExpression -> false
            is KtParenthesizedExpression -> expression.shouldKeepValue(usageCount)
            is KtArrayAccessExpression -> !sideEffectOnly ||
                    arrayExpression.shouldKeepValue(usageCount) ||
                    indexExpressions.any { it.shouldKeepValue(usageCount) }

            is KtBinaryExpression -> !sideEffectOnly ||
                    operationToken == KtTokens.IDENTIFIER ||
                    left.shouldKeepValue(usageCount) ||
                    right.shouldKeepValue(usageCount)

            is KtIfExpression -> !sideEffectOnly ||
                    condition.shouldKeepValue(usageCount) ||
                    then.shouldKeepValue(usageCount) ||
                    `else`.shouldKeepValue(usageCount)

            is KtBinaryExpressionWithTypeRHS -> !(sideEffectOnly && left.isNull())
            is KtClassLiteralExpression -> false
            is KtCallableReferenceExpression -> false
            null -> false
            else -> true
        }
    }

    protected fun MutableCodeToInline.convertToCallableReferenceIfNeeded(elementToBeReplaced: KtElement) {
        if (elementToBeReplaced !is KtCallableReferenceExpression) return
        val qualified = mainExpression?.safeAs<KtQualifiedExpression>() ?: return
        val reference = qualified.callExpression?.calleeExpression ?: qualified.selectorExpression ?: return
        val callableReference = if (elementToBeReplaced.receiverExpression == null) {
            psiFactory.createExpressionByPattern("::$0", reference)
        } else {
            psiFactory.createExpressionByPattern("$0::$1", qualified.receiverExpression, reference)
        }
        codeToInline.replaceExpression(qualified, callableReference)
    }

    inner class IntroduceValueForParameter(
        val parameter: Parameter,
        val value: KtExpression,
        val valueType: KotlinType?
    )

    protected open fun KtCallElement.insertExplicitTypeArgument() {}

    protected inner class Argument(
        val expression: KtExpression,
        val expressionType: KotlinType?,
        val isNamed: Boolean = false,
        val isDefaultValue: Boolean = false
    )

    protected abstract fun argumentForParameter(parameter: Parameter, callableDescriptor: CallableDescriptor): Argument?

    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    protected fun expandTypeArgumentsInParameterDefault(
        expression: KtExpression,
    ): KtExpression? {
        if (expression is KtCallExpression && expression.typeArguments.isEmpty() && expression.calleeExpression != null) {
            val arguments = allowAnalysisFromWriteAction {
                allowAnalysisOnEdt {
                    analyze(expression) {
                        getRenderedTypeArguments(expression)
                    }
                }
            }

            if (arguments != null) {
                val ktCallExpression = expression.copied()
                val callee = ktCallExpression.calleeExpression
                ktCallExpression.addAfter(psiFactory.createTypeArguments(arguments), callee)
                return ktCallExpression
            }
        }
        return null
    }

    protected abstract fun CallableDescriptor.valueParameters(): List<Parameter>
    protected abstract fun Parameter.name(): Name
    protected abstract fun introduceValue(
        value: KtExpression,
        valueType: KotlinType?,
        usages: Collection<KtExpression>,
        expressionToBeReplaced: KtExpression,
        nameSuggestion: String? = null,
        safeCall: Boolean = false
    )

    protected fun introduceVariablesForParameters(
        elementToBeReplaced: KtElement,
        receiver: KtExpression?,
        receiverType: KotlinType?,
        introduceValuesForParameters: Collection<IntroduceValueForParameter>
    ) {
        if (elementToBeReplaced is KtExpression) {
            if (receiver != null) {
                val thisReplaced = codeToInline.collectDescendantsOfType<KtExpression> { it.getCopyableUserData(RECEIVER_VALUE_KEY) != null }
                if (receiver.shouldKeepValue(usageCount = thisReplaced.size)) {
                    introduceValue(receiver, receiverType, thisReplaced, elementToBeReplaced)
                }
            }

            for (param in introduceValuesForParameters) {
                val usagesReplaced = codeToInline.collectDescendantsOfType<KtExpression> { it.getCopyableUserData(PARAMETER_VALUE_KEY) == param.parameter.name() }
                if (!usagesReplaced.isEmpty()) {
                    val p = 0
                }
                introduceValue(
                    param.value,
                    param.valueType,
                    usagesReplaced,
                    elementToBeReplaced,
                    nameSuggestion = param.parameter.name().asString()
                )
            }
        }
    }

    protected fun processValueParameterUsages(descriptor: CallableDescriptor): Collection<IntroduceValueForParameter> {
        val introduceValuesForParameters = ArrayList<IntroduceValueForParameter>()

        // process parameters in reverse order because default values can use previous parameters
        for (parameter in descriptor.valueParameters().asReversed()) {
            val argument = argumentForParameter(parameter, descriptor) ?: continue

            val parameterName = parameter.name()
            val expression: KtExpression = argument.expression.apply {
                if (this is KtCallElement) {
                    insertExplicitTypeArgument()
                }

                putCopyableUserData(PARAMETER_VALUE_KEY, parameterName)
            }

            val parameterUsages = codeToInline.collectDescendantsOfType<KtExpression> {
                it.getCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY) == parameterName
            }

            parameterUsages.forEach {
                val usageArgument = it.parent as? KtValueArgument
                if (argument.isNamed) {
                    usageArgument?.putCopyableUserData(MAKE_ARGUMENT_NAMED_KEY, Unit)
                }
                if (argument.isDefaultValue) {
                    usageArgument?.putCopyableUserData(DEFAULT_PARAMETER_VALUE_KEY, Unit)
                }

                codeToInline.replaceExpression(it, expression.copied())
            }

            if (expression.shouldKeepValue(usageCount = parameterUsages.size)) {
                introduceValuesForParameters.add(IntroduceValueForParameter(parameter, expression, argument.expressionType))
            }
        }

        return introduceValuesForParameters
    }

    protected fun <TypeParameter> processTypeParameterUsages(
        callElement: KtCallElement?,
        typeParameters: List<TypeParameter>,
        namer: (TypeParameter) -> Name,
        typeRetriever: (TypeParameter) -> KotlinType?,
        renderType: (KotlinType) -> String,
        isArrayType: (KotlinType) -> Boolean,
        renderClassifier: (KotlinType) -> String?
    ) {
        val explicitTypeArgs = callElement?.typeArgumentList?.arguments
        if (explicitTypeArgs != null && explicitTypeArgs.size != typeParameters.size) return

        for ((index, typeParameter) in typeParameters.withIndex()) {
            val parameterName = namer(typeParameter)
            val usages = codeToInline.collectDescendantsOfType<KtExpression> {
                it.getCopyableUserData(CodeToInline.TYPE_PARAMETER_USAGE_KEY) == parameterName
            }

            val type = typeRetriever(typeParameter) ?: continue
            val typeElement = if (explicitTypeArgs != null) { // we use explicit type arguments if available to avoid shortening
                val explicitArgTypeElement = explicitTypeArgs[index].typeReference?.typeElement ?: continue
                explicitArgTypeElement.putCopyableUserData(USER_CODE_KEY, Unit)
                explicitArgTypeElement
            } else {
                psiFactory.createType(renderType(type)).typeElement ?: continue
            }

            val typeClassifier = renderClassifier(type)

            for (usage in usages) {
                val parent = usage.parent
                if (parent is KtClassLiteralExpression && typeClassifier != null) {
                    // for class literal ("X::class") we need type arguments only for kotlin.Array
                    val arguments =
                        if (typeElement is KtUserType && isArrayType(type)) typeElement.typeArgumentList?.text.orEmpty()
                        else ""
                    codeToInline.replaceExpression(
                        usage, psiFactory.createExpression(typeClassifier + arguments)
                    )
                } else if (parent is KtUserType) {
                    parent.replace(typeElement)
                } else {
                    //TODO: tests for this?
                    codeToInline.replaceExpression(usage, psiFactory.createExpression(typeElement.text))
                }
            }
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    fun wrapCodeForSafeCall(receiver: KtExpression, receiverType: KotlinType?, expressionToBeReplaced: KtExpression) {
        if (codeToInline.statementsBefore.isEmpty()) {
            val qualified = codeToInline.mainExpression as? KtQualifiedExpression
            if (qualified != null) {
                if (qualified.receiverExpression.getCopyableUserData(RECEIVER_VALUE_KEY) != null) {
                    if (qualified is KtSafeQualifiedExpression) return // already safe
                    val selector = qualified.selectorExpression
                    if (selector != null) {
                        codeToInline.mainExpression = psiFactory.createExpressionByPattern("$0?.$1", receiver, selector)
                        return
                    }
                }
            }
        }

        if (codeToInline.statementsBefore.isEmpty() || allowAnalysisOnEdt { allowAnalysisFromWriteAction { analyze(expressionToBeReplaced) { expressionToBeReplaced.isUsedAsExpression } } }) {
            val thisReplaced = codeToInline.collectDescendantsOfType<KtExpression> { it.getCopyableUserData(RECEIVER_VALUE_KEY) != null }
            introduceValue(receiver, receiverType, thisReplaced, expressionToBeReplaced, safeCall = true)
        } else {
            codeToInline.mainExpression = psiFactory.buildExpression {
                appendFixedText("if (")
                appendExpression(receiver)
                appendFixedText("!=null)")
                appendFixedText(" {")
                with(codeToInline) {
                    appendExpressionsFromCodeToInline(postfixForMainExpression = "\n")
                }

                appendFixedText("}")
            }

            codeToInline.statementsBefore.clear()
        }
    }

    protected fun findAndMarkNewDeclarations() {
        for (it in codeToInline.statementsBefore) {
            if (it is KtNamedDeclaration) {
                it.putCopyableUserData(NEW_DECLARATION_KEY, Unit)
            }
        }
    }

    companion object {
        fun canBeReplaced(element: KtElement): Boolean = when (element) {
            is KtExpression, is KtAnnotationEntry, is KtSuperTypeCallEntry -> true
            else -> false
        }
    }
}