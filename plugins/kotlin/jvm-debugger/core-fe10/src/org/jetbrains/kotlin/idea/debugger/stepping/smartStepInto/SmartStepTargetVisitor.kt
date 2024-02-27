// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.actions.MethodSmartStepTarget
import com.intellij.debugger.actions.SmartStepTarget
import com.intellij.psi.PsiMethod
import com.intellij.util.Range
import com.intellij.util.containers.OrderedSet
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.calls.KtSuccessCallInfo
import org.jetbrains.kotlin.analysis.api.calls.KtVariableAccessCall
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.codeinsight.utils.getCallExpressionSymbol
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.isInlineOnly
import org.jetbrains.kotlin.idea.debugger.core.isInlineClass
import org.jetbrains.kotlin.idea.debugger.core.stepping.getLineRange
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.kotlin.internalName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.DescriptorUtils

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
        if (checkLineRangeFits(expression.getLineRange())) {
            recordCallableReference(expression)
        }
        super.visitCallableReferenceExpression(expression)
    }

    private fun recordCallableReference(expression: KtCallableReferenceExpression) {
        analyze(expression) {
            val symbol = expression.callableReference.mainReference.resolveToSymbol() ?: return
            if (symbol is KtPropertySymbol) {
                recordProperty(expression, symbol)
                return
            }
            if (symbol is KtFunctionSymbol) {
                val declaration = symbol.psi ?: return
                if (symbol.origin == KtSymbolOrigin.JAVA && declaration is PsiMethod) {
                    append(MethodSmartStepTarget(declaration, null, expression, true, lines))
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
                }
            }
        }
    }

    context(KtAnalysisSession)
    private fun recordProperty(expression: KtExpression, symbol: KtPropertySymbol) {
        val targetType = (expression as? KtNameReferenceExpression)?.computeTargetType()
        if (symbol is KtSyntheticJavaPropertySymbol) {
            val propertyAccessSymbol = when (targetType) {
                KtNameReferenceExpressionUsage.PROPERTY_GETTER, KtNameReferenceExpressionUsage.UNKNOWN, null -> symbol.javaGetterSymbol
                KtNameReferenceExpressionUsage.PROPERTY_SETTER -> symbol.javaSetterSymbol
            } ?: return
            val declaration = propertyAccessSymbol.psi
            if (declaration is PsiMethod) {
                append(MethodSmartStepTarget(declaration, null, expression, true, lines))
            }
            return
        }

        val propertyAccessSymbol = when (targetType) {
            KtNameReferenceExpressionUsage.PROPERTY_GETTER, KtNameReferenceExpressionUsage.UNKNOWN, null -> symbol.getter
            KtNameReferenceExpressionUsage.PROPERTY_SETTER -> symbol.setter
        } ?: return
        if (propertyAccessSymbol.isDefault) return
        if (symbol.isDelegatedProperty) {
            val property = symbol.psi as? KtProperty ?: return
            val delegate = property.delegate ?: return
            val delegatedMethod = findDelegatedMethod(delegate, targetType) ?: return
            val delegatedSymbol = delegatedMethod.getSymbol() as? KtFunctionLikeSymbol ?: return
            val methodInfo = CallableMemberInfo(delegatedSymbol)
            val label = propertyAccessLabel(symbol, delegatedSymbol)
            appendPropertyFilter(methodInfo, delegatedMethod, label, expression, lines)
            return
        }
        val property = propertyAccessSymbol.psi as? KtDeclaration ?: return
        if (property is KtPropertyAccessor && property.hasBody()) {
            val methodName = if (targetType == KtNameReferenceExpressionUsage.PROPERTY_SETTER) symbol.javaSetterName ?: return else symbol.javaGetterName
            val methodInfo = CallableMemberInfo(propertyAccessSymbol, name = methodName.asString())
            val label = propertyAccessLabel(symbol, propertyAccessSymbol)
            appendPropertyFilter(methodInfo, property, label, expression, lines)
        }
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

    context(KtAnalysisSession)
    private fun propertyAccessLabel(symbol: KtPropertySymbol, propertyAccessSymbol: KtDeclarationSymbol) =
        "${symbol.name}.${KotlinMethodSmartStepTarget.calcLabel(propertyAccessSymbol)}"

    private fun KtNameReferenceExpression.computeTargetType(): KtNameReferenceExpressionUsage {
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
            val declaration = methodSymbol.psi as? KtDeclaration ?: return false
            val callerMethodOrdinal = countExistingMethodCalls(declaration)
            val lambdaInfo = if (argumentSymbol.returnType.isFunctionalInterfaceType) {
                val samClassSymbol = argumentSymbol.returnType.expandedClassSymbol ?: return false
                val scope = samClassSymbol.getMemberScope()
                val funMethodSymbol = scope.getCallableSymbols()
                    .filterIsInstance<KtFunctionSymbol>()
                    .singleOrNull { it.modality == Modality.ABSTRACT }
                    ?: return false
                KotlinLambdaInfo(methodSymbol, argumentSymbol, callerMethodOrdinal,
                                 isNameMangledInBytecode = funMethodSymbol.containsInlineClassInValueArguments(),
                                 isSam = true, isSamSuspendMethod = funMethodSymbol.isSuspend, methodName = funMethodSymbol.name.asString())
            } else {
                val isNameMangledInBytecode = (argumentSymbol.returnType as? KtFunctionalType)?.parameterTypes
                    ?.any { it.expandedClassSymbol?.isInlineClass() == true } == true
                KotlinLambdaInfo(methodSymbol, argumentSymbol, callerMethodOrdinal,
                                 isNameMangledInBytecode = isNameMangledInBytecode)
            }
            append(KotlinLambdaSmartStepTarget(function, declaration, lines, lambdaInfo))
            return true
        }
    }

    private fun countExistingMethodCalls(declaration: KtDeclaration): Int {
        return consumer
            .filterIsInstance<KotlinMethodSmartStepTarget>()
            .count {
                val targetDeclaration = it.getDeclaration()
                targetDeclaration != null && targetDeclaration === declaration
            }
    }

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
        val calleeExpression = expression.calleeExpression
        if (calleeExpression != null && checkLineRangeFits(expression.getLineRange())) {
            recordFunctionCall(expression, calleeExpression)
        }
        super.visitCallExpression(expression)
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        if (checkLineRangeFits(expression.getLineRange())) {
            analyze(expression) {
                val resolvedCall = expression.resolveCall() as? KtSuccessCallInfo ?: return
                val variableAccessCall = resolvedCall.call as? KtVariableAccessCall ?: return
                val symbol = variableAccessCall.partiallyAppliedSymbol.symbol as? KtPropertySymbol ?: return
                recordProperty(expression, symbol)
            }
        }
        super.visitSimpleNameExpression(expression)
    }

    private fun recordFunctionCall(expression: KtExpression, highlightExpression: KtExpression) {
        analyze(expression) {
            val resolvedCall = expression.resolveCall()?.successfulFunctionCallOrNull() ?: return
            val symbol = resolvedCall.partiallyAppliedSymbol.symbol
            if (symbol.annotations.any { it.classId?.internalName == "kotlin/internal/IntrinsicConstEvaluation" }) {
                return
            }

            val declaration = symbol.psi
                // null is returned for implemented by delegation methods in K1
                ?: if (symbol is KtFunctionSymbol) symbol.getAllOverriddenSymbols()
                    .firstNotNullOfOrNull { it.psi } else null

            if (symbol.origin == KtSymbolOrigin.JAVA) {
                if (declaration is PsiMethod) {
                    append(MethodSmartStepTarget(declaration, null, highlightExpression, false, lines))
                }
                return
            }

            if (declaration == null && !(symbol is KtFunctionSymbol && symbol.isBuiltinFunctionInvoke)) {
                return
            }

            if (declaration !is KtDeclaration?) return

            if (symbol is KtConstructorSymbol && symbol.isPrimary) {
                if (declaration is KtClass && declaration.getAnonymousInitializers().isEmpty()) {
                    // There is no constructor or init block, so do not show it in smart step into
                    return
                }
            }

            // We can't step into @InlineOnly callables as there is no LVT, so skip them
            if (declaration is KtCallableDeclaration && declaration.isInlineOnly()) {
                return
            }

            val callLabel = KotlinMethodSmartStepTarget.calcLabel(symbol)
            val label = if (symbol is KtFunctionSymbol && symbol.isBuiltinFunctionInvoke && highlightExpression is KtSimpleNameExpression) {
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
                    CallableMemberInfo(symbol)
                )
            )
        }
    }

    private fun checkLineRangeFits(lineRange: IntRange?): Boolean =
        lineRange != null && lines.isWithin(lineRange.first) && lines.isWithin(lineRange.last)
}

private fun PropertyAccessorDescriptor.getJvmMethodName(): String =
    DescriptorUtils.getJvmName(this) ?: when (this) {
        is PropertySetterDescriptor -> JvmAbi.setterName(correspondingProperty.name.asString())
        else -> JvmAbi.getterName(correspondingProperty.name.asString())
    }

fun DeclarationDescriptor.getMethodName() =
    when (this) {
        is ClassDescriptor, is ConstructorDescriptor -> "<init>"
        is PropertyAccessorDescriptor -> getJvmMethodName()
        else -> name.asString()
    }
