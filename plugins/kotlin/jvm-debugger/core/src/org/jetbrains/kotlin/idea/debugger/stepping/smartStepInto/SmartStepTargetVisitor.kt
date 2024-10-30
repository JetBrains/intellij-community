// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.actions.MethodSmartStepTarget
import com.intellij.debugger.actions.SmartStepTarget
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.util.Range
import com.intellij.util.containers.OrderedSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.codeinsight.utils.getCallExpressionSymbol
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveFunctionCall
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.isInlineOnly
import org.jetbrains.kotlin.idea.debugger.core.isInlineClass
import org.jetbrains.kotlin.idea.debugger.core.stepping.getLineRange
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.kotlin.internalName
import org.jetbrains.kotlin.psi.*

// TODO support class initializers, local functions, delegated properties with specified type, setter for properties
class SmartStepTargetVisitor(
    private val lines: Range<Int>,
    private val consumer: OrderedSet<SmartStepTarget>
) : KtTreeVisitorVoid() {
    private fun append(target: SmartStepTarget) {
        consumer += target
    }

    override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
        if (checkLineRangeFits(lambdaExpression.getLineRange())) {
            recordFunction(lambdaExpression.functionLiteral)
        }
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        if (checkLineRangeFits(function.getLineRange()) && !recordFunction(function)) {
            super.visitNamedFunction(function)
        }
    }

    override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression) {
        if (checkLineRangeFits(expression.getLineRange()) && !recordCallableReference(expression)) {
            super.visitCallableReferenceExpression(expression)
        }
    }

    private fun recordCallableReference(expression: KtCallableReferenceExpression): Boolean {
        analyze(expression) {
            val symbol = expression.callableReference.mainReference.resolveToSymbol() ?: return false
            if (symbol is KaPropertySymbol) {
                return recordProperty(expression, symbol)
            }
            if (symbol is KaNamedFunctionSymbol) {
                val declaration = symbol.psi ?: return false
                if (declaration is PsiMethod) {
                    append(MethodSmartStepTarget(declaration, null, expression, true, lines))
                    return true
                } else if (declaration is KtNamedFunction) {
                    val label = KotlinMethodSmartStepTarget.calcLabel(symbol)
                    append(
                        KotlinMethodReferenceSmartStepTarget(
                            lines,
                            expression,
                            label,
                            declaration,
                            CallableMemberInfo(symbol)
                        )
                    )
                    return true
                }
            }
            return false
        }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun recordProperty(expression: KtExpression, symbol: KaPropertySymbol): Boolean {
        if (expression !is KtNameReferenceExpression && expression !is KtCallableReferenceExpression) return false
        val targetType = expression.computeTargetType()
        if (symbol is KaSyntheticJavaPropertySymbol) {
            val propertyAccessSymbol = when (targetType) {
                KtNameReferenceExpressionUsage.PROPERTY_GETTER, KtNameReferenceExpressionUsage.UNKNOWN -> symbol.javaGetterSymbol
                KtNameReferenceExpressionUsage.PROPERTY_SETTER -> symbol.javaSetterSymbol
            } ?: return false
            val declaration = propertyAccessSymbol.psi
            if (declaration is PsiMethod) {
                append(MethodSmartStepTarget(declaration, null, expression, true, lines))
                return true
            }
            return false
        }

        val propertyAccessSymbol = when (targetType) {
            KtNameReferenceExpressionUsage.PROPERTY_GETTER, KtNameReferenceExpressionUsage.UNKNOWN -> symbol.getter
            KtNameReferenceExpressionUsage.PROPERTY_SETTER -> symbol.setter
        } ?: return false
        if (propertyAccessSymbol.isDefault) return false
        if (symbol.isDelegatedProperty) {
            val property = symbol.psi as? KtProperty ?: return false
            val delegate = property.delegate ?: return false
            val delegatedMethod = findDelegatedMethod(delegate, targetType) ?: return false
            val delegatedSymbol = delegatedMethod.symbol as? KaFunctionSymbol ?: return false
            val methodInfo = CallableMemberInfo(delegatedSymbol, countExistingMethodCalls(delegatedMethod))
            val label = propertyAccessLabel(symbol, delegatedSymbol)
            appendPropertyFilter(methodInfo, delegatedMethod, label, expression, lines)
            return true
        }
        val property = propertyAccessSymbol.psi as? KtDeclaration ?: return false
        if (property is KtPropertyAccessor && property.hasBody()) {
            val methodName = if (targetType == KtNameReferenceExpressionUsage.PROPERTY_SETTER) {
                symbol.javaSetterName ?: return false
            } else {
                symbol.javaGetterName
            }
            val methodInfo = CallableMemberInfo(
                propertyAccessSymbol,
                ordinal = countExistingMethodCalls(property),
                name = methodName.asString()
            )
            val label = propertyAccessLabel(symbol, propertyAccessSymbol)
            appendPropertyFilter(methodInfo, property, label, expression, lines)
            return true
        }
        return false
    }

    /**
     * This is a workaround to find the delegated method in the delegated class.
     * Should be replaced with an API call when there is one.
     */
    private fun findDelegatedMethod(delegate: KtPropertyDelegate, targetType: KtNameReferenceExpressionUsage?): KtFunction? {
        val reference = delegate.mainReference ?: return null
        val expectedMethodName = if (targetType == KtNameReferenceExpressionUsage.PROPERTY_SETTER) "setValue" else "getValue"
        return reference.multiResolve(false).asSequence()
            .filter { it.isValidResult }
            .map { it.element }
            .filterIsInstance<KtFunction>()
            .filter { it.name == expectedMethodName }
            .singleOrNull()
    }

    context(KaSession)
    private fun propertyAccessLabel(symbol: KaPropertySymbol, propertyAccessSymbol: KaDeclarationSymbol) =
        "${symbol.name}.${KotlinMethodSmartStepTarget.calcLabel(propertyAccessSymbol)}"

    private fun KtExpression.computeTargetType(): KtNameReferenceExpressionUsage {
        val potentialLeftHandSide = parent as? KtQualifiedExpression ?: this
        val binaryExpr =
            potentialLeftHandSide.parent as? KtBinaryExpression ?: return KtNameReferenceExpressionUsage.PROPERTY_GETTER
        return when (binaryExpr.operationToken) {
            KtTokens.EQ -> KtNameReferenceExpressionUsage.PROPERTY_SETTER
            else -> KtNameReferenceExpressionUsage.UNKNOWN
        }
    }

    private enum class KtNameReferenceExpressionUsage {
        PROPERTY_GETTER, PROPERTY_SETTER, UNKNOWN
    }

    private fun appendPropertyFilter(
        methodInfo: CallableMemberInfo,
        declaration: KtDeclarationWithBody,
        label: String,
        expression: KtExpression,
        lines: Range<Int>
    ) {
        when (expression) {
            is KtCallableReferenceExpression ->
                append(
                    KotlinMethodReferenceSmartStepTarget(
                        lines,
                        expression,
                        label,
                        declaration,
                        methodInfo
                    )
                )
            else -> {
                val ordinal = countExistingMethodCalls(declaration)
                append(
                    KotlinMethodSmartStepTarget(
                        lines,
                        expression,
                        label,
                        declaration,
                        ordinal,
                        methodInfo
                    )
                )
            }
        }
    }

    private fun recordFunction(function: KtFunction): Boolean {
        analyze(function) {
            val (methodSymbol, argumentSymbol) = getCallExpressionSymbol(function) ?: return false
            val declaration = methodSymbol.psi
            val lambdaInfo = when (declaration) {
                is PsiMethod -> createJavaLambdaInfo(declaration, methodSymbol, argumentSymbol)
                is KtDeclaration -> createKotlinLambdaInfo(declaration, methodSymbol, argumentSymbol)
                else -> return false
            } ?: return false
            append(KotlinLambdaSmartStepTarget(function, declaration, lines, lambdaInfo))
            return true
        }
    }

    context(KaSession)
    private fun createJavaLambdaInfo(
        declaration: PsiMethod,
        methodSymbol: KaFunctionSymbol,
        argumentSymbol: KaValueParameterSymbol,
    ): KotlinLambdaInfo {
        val callerMethodOrdinal = countExistingMethodCalls(declaration)
        return KotlinLambdaInfo(methodSymbol, argumentSymbol, callerMethodOrdinal, isNameMangledInBytecode = false)
    }

    context(KaSession)
    private fun createKotlinLambdaInfo(
        declaration: KtDeclaration,
        methodSymbol: KaFunctionSymbol,
        argumentSymbol: KaValueParameterSymbol,
    ): KotlinLambdaInfo? {
        val callerMethodOrdinal = countExistingMethodCalls(declaration)
        return if (argumentSymbol.returnType.isFunctionalInterface) {
            val samClassSymbol = argumentSymbol.returnType.expandedSymbol ?: return null
            val scope = samClassSymbol.memberScope
            val funMethodSymbol = scope.callables
                .filterIsInstance<KaNamedFunctionSymbol>()
                .singleOrNull { it.modality == KaSymbolModality.ABSTRACT }
                ?: return null
            KotlinLambdaInfo(
                methodSymbol, argumentSymbol, callerMethodOrdinal,
                isNameMangledInBytecode = funMethodSymbol.containsInlineClassInParameters(),
                isSam = true, isSamSuspendMethod = funMethodSymbol.isSuspend, methodName = funMethodSymbol.name.asString()
            )
        } else {
            val isNameMangledInBytecode = (argumentSymbol.returnType as? KaFunctionType)?.parameterTypes
                ?.any { it.expandedSymbol?.isInlineClass() == true } == true
            KotlinLambdaInfo(
                methodSymbol, argumentSymbol, callerMethodOrdinal,
                isNameMangledInBytecode = isNameMangledInBytecode
            )
        }
    }

    private fun countExistingMethodCalls(declaration: KtDeclaration): Int = consumer.targetsWithDeclaration(declaration).count()

    private fun countExistingMethodCalls(psiMethod: PsiMethod): Int =
        consumer.filterIsInstance<MethodSmartStepTarget>().count { psiMethod === it.method }

    override fun visitObjectLiteralExpression(expression: KtObjectLiteralExpression) {
        // Skip calls in object declarations
    }

    override fun visitIfExpression(expression: KtIfExpression) {
        expression.condition?.accept(this)
    }

    override fun visitWhileExpression(expression: KtWhileExpression) {
        expression.condition?.accept(this)
    }

    override fun visitDoWhileExpression(expression: KtDoWhileExpression) {
        expression.condition?.accept(this)
    }

    override fun visitForExpression(expression: KtForExpression) {
        expression.loopRange?.accept(this)
    }

    override fun visitWhenExpression(expression: KtWhenExpression) {
        expression.subjectExpression?.accept(this)
    }

    override fun visitArrayAccessExpression(expression: KtArrayAccessExpression) {
        super.visitArrayAccessExpression(expression)
        if (checkLineRangeFits(expression.getLineRange())) {
            recordFunctionCall(expression, expression)
        }
    }

    override fun visitUnaryExpression(expression: KtUnaryExpression) {
        super.visitUnaryExpression(expression)
        if (checkLineRangeFits(expression.getLineRange())) {
            recordFunctionCall(expression, expression.operationReference)
        }
    }

    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        super.visitBinaryExpression(expression)
        if (checkLineRangeFits(expression.getLineRange())) {
            recordFunctionCall(expression, expression.operationReference)
        }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        // Collect arguments first for correct method ordinals.
        // Here `_i` suffixes represent the ordinals:
        // foo_2(foo_1(foo_0())) + foo_3()
        super.visitCallExpression(expression)
        val calleeExpression = expression.calleeExpression
        if (calleeExpression != null && checkLineRangeFits(expression.getLineRange())) {
            recordFunctionCall(expression, calleeExpression)
        }
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        if (checkLineRangeFits(expression.getLineRange())) {
            analyze(expression) {
                val variableAccessCall = expression.resolveToCall()?.successfulCallOrNull<KaVariableAccessCall>() ?: return
                val symbol = variableAccessCall.partiallyAppliedSymbol.symbol as? KaPropertySymbol ?: return
                recordProperty(expression, symbol)
            }
        }
        super.visitSimpleNameExpression(expression)
    }

    private fun recordFunctionCall(expression: KtExpression, highlightExpression: KtExpression) {
        analyze(expression) {
            val resolvedCall = resolveFunctionCall(expression) ?: return
            val symbol = resolvedCall.partiallyAppliedSymbol.symbol
            if (symbol.annotations.any { it.classId?.internalName == "kotlin/internal/IntrinsicConstEvaluation" }) {
                return
            }

            val declaration = getFunctionDeclaration(symbol)
            if (declaration is PsiMethod) {
                append(MethodSmartStepTarget(declaration, null, highlightExpression, false, lines))
                return
            }

            if (declaration == null && !(symbol.isInvoke())) {
                return
            }

            if (declaration !is KtDeclaration?) return

            // We can't step into @InlineOnly callables as there is no LVT, so skip them
            if (declaration is KtCallableDeclaration && declaration.isInlineOnly()) {
                return
            }

            val callLabel = KotlinMethodSmartStepTarget.calcLabel(symbol)
            val label = if (symbol.isInvoke() && highlightExpression is KtSimpleNameExpression) {
                "${highlightExpression.text}.$callLabel"
            } else {
                callLabel
            }

            val ordinal = if (declaration == null) 0 else countExistingMethodCalls(declaration)
            append(
                KotlinMethodSmartStepTarget(
                    lines,
                    highlightExpression,
                    label,
                    declaration,
                    ordinal,
                    CallableMemberInfo(symbol, ordinal)
                )
            )
        }
    }

    context(KaSession)
    private fun getFunctionDeclaration(symbol: KaFunctionSymbol): PsiElement? {
        if (symbol.isInvoke()) return null
        symbol.psi?.let { return it }
        // null is returned for implemented by delegation methods in K1
        if (symbol !is KaNamedFunctionSymbol) return null
        return symbol.allOverriddenSymbols.firstNotNullOfOrNull { it.psi }
    }

    private fun checkLineRangeFits(lineRange: IntRange?): Boolean =
        lineRange != null && lines.isWithin(lineRange.first) && lines.isWithin(lineRange.last)
}

@VisibleForTesting
@ApiStatus.Internal
fun Collection<SmartStepTarget>.targetsWithDeclaration(declaration: KtDeclaration?): Sequence<KotlinMethodSmartStepTarget> {
    if (declaration == null) return emptySequence()
    return asSequence().filterIsInstance<KotlinMethodSmartStepTarget>().filter {
        val targetDeclaration = it.getDeclaration() ?: return@filter false
        areElementsEquivalent(declaration, targetDeclaration)
    }
}
