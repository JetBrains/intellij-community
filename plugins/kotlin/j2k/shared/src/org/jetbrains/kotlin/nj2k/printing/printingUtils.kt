// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.printing

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiType
import org.jetbrains.kotlin.nj2k.escaped
import org.jetbrains.kotlin.nj2k.isPresent
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.isKotlinFunctionalType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter

fun String.escapedAsQualifiedName(): String =
    split('.')
        .map { it.escaped() }
        .joinToString(".") { it }

// Similar to `MoveLambdaOutsideParenthesesInspection` but less comprehensive,
// especially where functional interfaces are concerned
internal fun canMoveLambdaOutsideParentheses(argumentList: JKArgumentList): Boolean {
    val arguments = argumentList.arguments

    if (arguments.count { it.value is JKLambdaExpression } != 1) {
        // If there are multiple lambdas, leave them inside parentheses for code style reasons
        return false
    }

    val lambda = arguments.lastOrNull()?.value as? JKLambdaExpression ?: return false

    val call = argumentList.parent
    if (call !is JKCallExpressionImpl && call !is JKNewExpression) return false

    if ((call as? JKCallExpressionImpl)?.canMoveLambdaOutsideParentheses == true ||
        (call as? JKNewExpression)?.canMoveLambdaOutsideParentheses == true
    ) {
        // Known (library) method, skip additional checks
        return true
    }

    if (lambda.functionalType.isPresent()) {
        // If the functional type (SAM constructor) exists and is not redundant,
        // then the lambda can't be moved outside the parentheses.
        // However, if it is redundant, we can move the lambda and not print the functional type.
        //
        // Currently, we don't try to determine if the SAM constructor
        // is redundant or not in J2K and rely on `RedundantSamConstructorInspection` for that.
        return false
    }

    // Check that the called method has a compatible signature
    val callPsi = call.psi
    val method = when {
        callPsi is PsiMethodCallExpression -> callPsi.resolveMethod()
        callPsi is PsiNewExpression -> callPsi.resolveMethod()
        call is JKCallExpressionImpl -> call.identifier.target
        else -> return false
    }

    return when (method) {
        is KtNamedFunction -> {
            val lastParameter = method.valueParameters.lastOrNull()
            method.valueParameters.size == arguments.size &&
                    lastParameter?.isVarArg != true &&
                    lastParameter?.hasFunctionalType() == true
        }

        is PsiMethod -> {
            val lastParameter = method.parameterList.parameters.lastOrNull()
            method.parameterList.parametersCount == arguments.size &&
                    lastParameter?.isVarArgs != true &&
                    lastParameter?.type?.isFunctionalType() == true
        }

        is JKMethod -> {
            method.parameters.size == arguments.size && !method.parameters.last().isVarArgs
        }

        else -> false
    }
}

private fun PsiType.isFunctionalType(): Boolean {
    if (isKotlinFunctionalType) return true
    val fqn = canonicalText.substringBefore("<")
    return fqn in javaFunctionalTypes
}

private val javaFunctionalTypes: Set<String> = setOf(
    "java.util.function.BiConsumer",
    "java.util.function.BiFunction",
    "java.util.function.BinaryOperator",
    "java.util.function.Consumer",
    "java.util.function.Function",
    "java.util.function.Predicate",
    "java.util.function.Supplier",
    "java.util.function.UnaryOperator",
)

private fun KtParameter.hasFunctionalType(): Boolean =
    isLambdaParameter || isFunctionTypeParameter || ") -> " in typeReference?.text.orEmpty()