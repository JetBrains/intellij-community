// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline.codeInliner

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.returnType
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.function
import org.jetbrains.kotlin.analysis.api.resolution.simple
import org.jetbrains.kotlin.analysis.api.resolution.single
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaContextParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.arrayElementType
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.codeinsight.utils.getRenderedTypeArguments
import org.jetbrains.kotlin.idea.k2.refactoring.util.LambdaToAnonymousFunctionUtil
import org.jetbrains.kotlin.idea.k2.refactoring.util.createReplacementForContextArgument
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.CodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.ARTIFICIAL_VARARG_ARRAY_VALUE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.DEFAULT_PARAMETER_VALUE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.MAKE_ARGUMENT_NAMED_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.PARAMETER_VALUE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.SIDE_EFFECTS
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.USER_CODE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.WAS_CONVERTED_TO_FUNCTION_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.WAS_FUNCTION_LITERAL_ARGUMENT_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.MutableCodeToInline
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.NonLocalJumpToken
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.collectDescendantsOfType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.expectDeclarationIfAny
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionWithLabel
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.LambdaArgument
import org.jetbrains.kotlin.psi.buildExpression
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getAssignmentByLHS
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.kotlin.resolution.KtResolvableCall
import org.jetbrains.kotlin.types.Variance

internal class IntroduceValueForParameter(
    val parameterName: Name,
    val value: KtExpression,
    val isValueNullable: Boolean?,
)

internal class ValueParameterInliner(
    private val call: KtElement,
    private val codeToInline: MutableCodeToInline,
    private val originalDeclaration: KtDeclaration?,
) {
    private val psiFactory = KtPsiFactory(call.project)

    private val mapping: Map<KtExpression, Name>? = analyze(call) {
        val treeUpToCall = call.treeUpToCall() as? KtResolvableCall ?: return@analyze null
        treeUpToCall.tryResolveCall()?.single?.function?.valueArgumentMapping?.mapValues { e -> e.value.name }
    }

    private val contextArguments: List<String?>? = analyze(call) {
        val treeUpToCall = call.treeUpToCall() as? KtResolvableCall ?: return@analyze null
        treeUpToCall.tryResolveCall()?.single?.simple?.contextArguments?.map {
            createReplacementForContextArgument(it)
        }
    }

    private val explicitContextArguments: Map<Name, String>? = analyze(call) {
        val treeUpToCall = call.treeUpToCall() as? KtResolvableCall ?: return@analyze null
        val singleCall = treeUpToCall.tryResolveCall()?.single?.simple ?: return@analyze null
        singleCall.symbol.contextParameters.zip(singleCall.contextArguments)
            .mapNotNull<Pair<KaContextParameterSymbol, KaReceiverValue>, Pair<Name, @NlsSafe String>> { (cp, cpArg) ->
                val explicitArg = (cpArg as? KaExplicitReceiverValue)?.expression?.text ?: return@mapNotNull null
                cp.name to explicitArg
            }.toMap()
    }

    fun processValueParameterUsages(declaration: KtDeclaration): Collection<IntroduceValueForParameter> {
        val introduceValuesForParameters = ArrayList<IntroduceValueForParameter>()

        // process parameters in reverse order because default values can use previous parameters
        for (parameter in declaration.valueParameters().asReversed()) {
            val argument = argumentForParameter(parameter, declaration) ?: continue

            val parameterName = parameter.name()
            val expression: KtExpression = argument.expression.apply {
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
                introduceValuesForParameters.add(IntroduceValueForParameter(parameterName, expression, argument.isExpressionNullable))
            }
        }

        processContextParameterUsages()

        return introduceValuesForParameters
    }

    private fun processContextParameterUsages() {
        codeToInline.collectDescendantsOfType<KtSimpleNameExpression>().forEach { expr ->
            val contextParameterUsage = expr.getCopyableUserData(CodeToInline.CONTEXT_PARAMETER_USAGE_KEY)
            val oldExpression = expr.parent
            if (contextParameterUsage != null && oldExpression is KtCallExpression) {
                val calleeExpression = oldExpression.calleeExpression ?: return@forEach
                val args = contextParameterUsage.mapNotNull { (calleeName, containerName) ->
                    explicitContextArguments?.get(containerName)?.let {
                        "${calleeName.asString()} = $it"
                    }
                }.joinToString()
                if (args.isEmpty()) return@forEach
                val firstArgs =
                    (oldExpression.valueArgumentList?.copy() as? KtValueArgumentList)?.arguments?.joinToString { it.text }
                val argsList = if (firstArgs != null) "$firstArgs, $args" else args
                val replacement = KtPsiFactory.contextual(expr).createExpression(calleeExpression.text + "($argsList)" + oldExpression.lambdaArguments.joinToString { it.text })
                codeToInline.replaceExpression(oldExpression, replacement)
            }
        }
    }

    private fun KtDeclaration.valueParameters(): List<KtParameter> =
      (this as? KtModifierListOwner)?.modifierList?.contextParameterList?.contextParameters.orEmpty() +
                (this as? KtDeclarationWithBody)?.valueParameters.orEmpty()

    private fun KtParameter.name(): Name {
        val declaration = originalDeclaration
        val isAnonymousFunction = declaration is KtNamedFunction && declaration.nameIdentifier == null
        val isAnonymousFunctionWithReceiver = isAnonymousFunction && declaration.receiverTypeReference != null

        return if (isAnonymousFunction && ownerDeclaration == declaration) {
            val shift = if (isAnonymousFunctionWithReceiver) 2 else 1
            Name.identifier("p${parameterIndex() + shift}")
        } else {
            nameAsSafeName
        }
    }

    private class Argument(
        val expression: KtExpression,
        val isExpressionNullable: Boolean?,
        val isNamed: Boolean = false,
        val isDefaultValue: Boolean = false
    )

    private fun argumentForParameter(
        parameter: KtParameter,
        callableDescriptor: KtDeclaration
    ): Argument? {
        if (callableDescriptor is KtPropertyAccessor && callableDescriptor.isSetter) {
            return argumentForPropertySetter()
        }

        if (parameter.isContextParameter) {
            val exprText = contextArguments?.getOrNull(parameter.parameterIndex()) ?: return null
            val resultExpression = KtPsiFactory(call.project).createExpressionCodeFragment(exprText, call).getContentElement() ?: return null
            resultExpression.putCopyableUserData(SIDE_EFFECTS, false)
            val isExpressionNullable = analyze(resultExpression) {
                isNullableType(resultExpression.expressionType)
            }
            return Argument(resultExpression, isExpressionNullable, isNamed = false, isDefaultValue = false)
        }

        val argumentExpressionsForParameter = mapping?.entries?.filter { (_, value) ->
            value == parameter.name()
        }?.map { it.key } ?: return null

        if (parameter.isVarArg) {
            return argumentForVarargParameter(argumentExpressionsForParameter, parameter)
        } else {
            return argumentForRegularParameter(argumentExpressionsForParameter, parameter, callableDescriptor)
        }
    }

    private fun argumentForRegularParameter(
        argumentExpressionsForParameter: List<KtExpression>, parameter: KtParameter, callableDeclaration: KtDeclaration
    ): Argument? {
        val expression = argumentExpressionsForParameter.firstOrNull() ?: getDefaultValue(parameter) ?: return null
        val parent = expression.parent
        val isNamed = (parent as? KtValueArgument)?.isNamed() == true
        var resultExpression = run {
            if (expression !is KtLambdaExpression) return@run null
            if (parent is LambdaArgument) {
                expression.putCopyableUserData(WAS_FUNCTION_LITERAL_ARGUMENT_KEY, Unit)
            }

            markNonLocalJumps(expression, parameter)

            val flag = analyze(call) {
                val functionType = expression.expressionType as? KaFunctionType
                (functionType)?.hasReceiver == true && !functionType.isSuspend
            }

            val functionText = if (flag) {
                //expand to function only for types with an extension
                LambdaToAnonymousFunctionUtil.prepareFunctionText(expression)
            } else {
                null
            }

            functionText?.let {
                val function = LambdaToAnonymousFunctionUtil.convertLambdaToFunction(expression, functionText)
                function.putCopyableUserData(WAS_CONVERTED_TO_FUNCTION_KEY, Unit)
                function
            }
        } ?: expression

        markAsUserCode(resultExpression)

        val isExpressionNullable = analyze(resultExpression) { isNullableType(resultExpression.expressionType) }
        if (argumentExpressionsForParameter.isEmpty() && callableDeclaration is KtFunction) {
            //encode default value
            val allParameters = callableDeclaration.valueParameters()
            expression.forEachDescendantOfType<KtSimpleNameExpression> {
                val target = it.mainReference.resolve()
                if (target is KtParameter && target in allParameters) {
                    it.putCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY, target.nameAsSafeName)
                }
            }

            resultExpression = expandTypeArgumentsInParameterDefault(expression) ?: resultExpression
        }

        return Argument(resultExpression, isExpressionNullable, isNamed = isNamed, argumentExpressionsForParameter.isEmpty())
    }

    private fun argumentForPropertySetter(): Argument? {
        val expr = (call as? KtExpression)
            ?.getQualifiedExpressionForSelectorOrThis()
            ?.getAssignmentByLHS()
            ?.right ?: return null
        return Argument(expr, analyze(call) { isNullableType(expr.expressionType) })
    }

    private fun argumentForVarargParameter(argumentExpressionsForParameter: List<KtExpression>, parameter: KtParameter): Argument? {
        val single = argumentExpressionsForParameter.singleOrNull()?.parent as? KtValueArgument
        if (single?.getSpreadElement() != null) {
            val expression = argumentExpressionsForParameter.first()
            markAsUserCode(expression)
            return analyze(call) {
                Argument(expression, isNullableType(expression.expressionType), isNamed = single.isNamed())
            }
        }

        val expression = analyze(parameter) {
            val parameterType = parameter.returnType
            val elementType = parameterType.arrayElementType ?: return null
            psiFactory.buildExpression {
                val arrayOfFunctionName = arrayOfFunctionName(elementType)
                appendFixedText(arrayOfFunctionName)
                if (argumentExpressionsForParameter.isEmpty() && elementType !is KaErrorType && arrayOfFunctionName == "kotlin.arrayOf") {
                    appendFixedText("<")
                    appendFixedText(elementType.render(position = Variance.INVARIANT))
                    appendFixedText(">")
                }
                appendFixedText("(")
                for ((i, argument) in argumentExpressionsForParameter.withIndex()) {
                    if (i > 0) appendFixedText(",")
                    val valueArgument = argument.parent as KtValueArgument
                    if (valueArgument.getSpreadElement() != null) {
                        appendFixedText("*")
                    }
                    val argumentExpression = valueArgument.getArgumentExpression()!!
                    markAsUserCode(argumentExpression)
                    appendExpression(argumentExpression)
                }

                appendFixedText(")")
            }
        }
        expression.putCopyableUserData(ARTIFICIAL_VARARG_ARRAY_VALUE_KEY, Unit)

        return analyze(expression) {
            Argument(expression, isNullableType(expression.expressionType))
        }
    }

    private fun getDefaultValue(parameter: KtParameter): KtExpression? {
        val ownerFunction = parameter.ownerFunction
        val defaultValueFromExpect = (ownerFunction
            ?.expectDeclarationIfAny()
            ?.takeIf { it != ownerFunction } as? KtCallableDeclaration)?.valueParameters()
            ?.get(parameter.parameterIndex())
            ?.defaultValue
        return defaultValueFromExpect ?: parameter.defaultValue
    }

    private fun expandTypeArgumentsInParameterDefault(
        expression: KtExpression,
    ): KtExpression? {
        if (expression is KtCallExpression && expression.typeArguments.isEmpty() && expression.calleeExpression != null) {
            val arguments = analyze(expression) { getRenderedTypeArguments(expression) }

            if (arguments != null) {
                val ktCallExpression = expression.copied()
                val callee = ktCallExpression.calleeExpression
                ktCallExpression.addAfter(psiFactory.createTypeArguments(arguments), callee)
                return ktCallExpression
            }
        }
        return null
    }

    context(_: KaSession)
    private fun arrayOfFunctionName(elementType: KaType): String {
        return when {
            elementType.classId == KaStandardTypeClassIds.INT -> "kotlin.intArrayOf"
            elementType.classId == KaStandardTypeClassIds.LONG -> "kotlin.longArrayOf"
            elementType.classId == KaStandardTypeClassIds.SHORT -> "kotlin.shortArrayOf"
            elementType.classId == KaStandardTypeClassIds.CHAR -> "kotlin.charArrayOf"
            elementType.classId == KaStandardTypeClassIds.BOOLEAN -> "kotlin.booleanArrayOf"
            elementType.classId == KaStandardTypeClassIds.BYTE -> "kotlin.byteArrayOf"
            elementType.classId == KaStandardTypeClassIds.DOUBLE -> "kotlin.doubleArrayOf"
            elementType.classId == KaStandardTypeClassIds.FLOAT -> "kotlin.floatArrayOf"
            elementType is KaErrorType -> "kotlin.arrayOf"
            else -> "kotlin.arrayOf"
        }
    }

    private fun markAsUserCode(expression: KtExpression) {
        // if type arguments were inserted at the preprocessing stage, markers are already set
        if (expression.children.all { it.getCopyableUserData(USER_CODE_KEY) == null }) {
            expression.putCopyableUserData(USER_CODE_KEY, Unit)
        }
    }

    private fun markNonLocalJumps(lambdaArgumentExpression: KtLambdaExpression, parameter: KtParameter) {
        val ownerDeclaration = parameter.ownerDeclaration
        if (ownerDeclaration !is KtNamedFunction || !ownerDeclaration.hasModifier(KtTokens.INLINE_KEYWORD)) return
        val isJumpPossible = lambdaArgumentExpression.languageVersionSettings.supportsFeature(LanguageFeature.BreakContinueInInlineLambdas)
        if (!isJumpPossible) return
        lambdaArgumentExpression.accept(NonLocalJumpVisitor(lambdaArgumentExpression))
    }
}

private class NonLocalJumpVisitor(val lambdaArgumentExpression: KtLambdaExpression) : KtTreeVisitorVoid() {
    override fun visitBreakExpression(expression: KtBreakExpression) {
        markIfNonLocal(expression)
    }

    override fun visitContinueExpression(expression: KtContinueExpression) {
        markIfNonLocal(expression)
    }

    private fun markIfNonLocal(expression: KtExpressionWithLabel) {
        if (expression.getTargetLabel() != null) return
        val loopForJump = expression.getStrictParentOfType<KtLoopExpression>() ?: return
        if (PsiTreeUtil.isAncestor(loopForJump, lambdaArgumentExpression, true)) {
            val loopToken = loopForJump.getCopyableUserData(InlineDataKeys.NON_LOCAL_JUMP_KEY) ?: NonLocalJumpToken()
            loopForJump.putCopyableUserData(InlineDataKeys.NON_LOCAL_JUMP_KEY, loopToken)
            expression.putCopyableUserData(InlineDataKeys.NON_LOCAL_JUMP_KEY, loopToken)
        }
    }
}
