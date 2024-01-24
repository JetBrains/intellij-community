// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import java.util.Collections

abstract class OutputValueBoxer<KotlinType>(val outputValues: List<OutputValue<KotlinType>>) {

    abstract val returnType: KotlinType

    protected abstract fun getBoxingExpressionPattern(arguments: List<KtExpression>): String?

    abstract val boxingRequired: Boolean

    fun getReturnExpression(arguments: List<KtExpression>, psiFactory: KtPsiFactory): KtReturnExpression? {
        val expressionPattern = getBoxingExpressionPattern(arguments) ?: return null
        return psiFactory.createExpressionByPattern("return $expressionPattern", *arguments.toTypedArray()) as KtReturnExpression
    }

    protected abstract fun extractExpressionByIndex(boxedExpression: KtExpression, index: Int): KtExpression?

    protected fun extractArgumentExpressionByIndex(boxedExpression: KtExpression, index: Int): KtExpression? {
        val call: KtCallExpression? = when (boxedExpression) {
            is KtCallExpression -> boxedExpression
            is KtQualifiedExpression -> boxedExpression.selectorExpression as? KtCallExpression
            else -> null
        }
        val arguments = call?.valueArguments
        if (arguments == null || arguments.size <= index) return null

        return arguments[index].getArgumentExpression()
    }

    fun extractExpressionByValue(boxedExpression: KtExpression, value: OutputValue<KotlinType>): KtExpression? {
        val index = outputValues.indexOf(value)
        if (index < 0) return null

        return extractExpressionByIndex(boxedExpression, index)
    }

    abstract fun getUnboxingExpressions(boxedText: String): Map<OutputValue<KotlinType>, String>

    abstract class AsTuple<KotlinType>(
        outputValues: List<OutputValue<KotlinType>>,
    ) : OutputValueBoxer<KotlinType>(outputValues) {
        init {
            assert(outputValues.size <= 3) { "At most 3 output values are supported" }
        }

        companion object {
            private val selectors = arrayOf("first", "second", "third")
        }

        override val boxingRequired: Boolean = outputValues.size > 1

        override fun getBoxingExpressionPattern(arguments: List<KtExpression>): String? {
            return when (arguments.size) {
                0 -> null
                1 -> "$0"
                else -> {
                    val constructorName = if (arguments.size == 2) "kotlin.Pair" else "kotlin.Triple"
                    return arguments.indices.joinToString(prefix = "$constructorName(", separator = ", ", postfix = ")") { "\$$it" }
                }
            }
        }

        override fun extractExpressionByIndex(boxedExpression: KtExpression, index: Int): KtExpression? {
            if (outputValues.size == 1) return boxedExpression
            return extractArgumentExpressionByIndex(boxedExpression, index)
        }

        override fun getUnboxingExpressions(boxedText: String): Map<OutputValue<KotlinType>, String> {
            return when (outputValues.size) {
                0 -> Collections.emptyMap()
                1 -> Collections.singletonMap(outputValues.first(), boxedText)
                else -> {
                    var i = 0
                    ContainerUtil.newMapFromKeys(outputValues.iterator()) { "$boxedText.${selectors[i++]}" }
                }
            }
        }
    }

    abstract class AsList<KotlinType>(outputValues: List<OutputValue<KotlinType>>) : OutputValueBoxer<KotlinType>(outputValues) {

        override val boxingRequired: Boolean = outputValues.isNotEmpty()

        override fun getBoxingExpressionPattern(arguments: List<KtExpression>): String? {
            if (arguments.isEmpty()) return null
            return arguments.indices.joinToString(prefix = "kotlin.collections.listOf(", separator = ", ", postfix = ")") { "\$$it" }
        }

        override fun extractExpressionByIndex(boxedExpression: KtExpression, index: Int): KtExpression? {
            return extractArgumentExpressionByIndex(boxedExpression, index)
        }

        override fun getUnboxingExpressions(boxedText: String): Map<OutputValue<KotlinType>, String> {
            var i = 0
            return ContainerUtil.newMapFromKeys(outputValues.iterator()) { "$boxedText[${i++}]" }
        }
    }
}
