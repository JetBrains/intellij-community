// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import org.jetbrains.kotlin.psi.KtExpression

data class OutputDescriptor<KotlinType>(
    val defaultResultExpression: KtExpression?,
    val typeOfDefaultFlow: KotlinType,
    val implicitReturn: KtExpression?,
    val lastExpressionHasNothingType: Boolean,
    val valuedReturnExpressions: List<KtExpression>,
    val returnValueType: KotlinType,
    val jumpExpressions: List<KtExpression>,
    val hasSingleTarget: Boolean,
    val sameExitForDefaultAndJump: Boolean
) {
    override fun toString(): String {
        return buildString {
            append("OutputDescriptor[").append("\n")
            append("defaultResultExpressions = ")
            defaultResultExpression?.let {
                append(it.text)
            }
            append("\n")
            append("typeOfDefaultFlow = ").append(typeOfDefaultFlow).append("\n")
            append("valuedReturnExpressions = ").append(valuedReturnExpressions.joinToString(separator = ",\n") { it.text }).append("\n")
            append("returnValueType = ").append(returnValueType).append("\n")
            append("jumpExpressions = ").append(jumpExpressions.joinToString(separator = ",\n") { it.text }).append("\n")
            append("hasSingleTarget = ").append(hasSingleTarget).append("\n")
            append("sameExitForDefaultAndJump = ").append(sameExitForDefaultAndJump).append("\n")
            append("]")
        }
    }
}
