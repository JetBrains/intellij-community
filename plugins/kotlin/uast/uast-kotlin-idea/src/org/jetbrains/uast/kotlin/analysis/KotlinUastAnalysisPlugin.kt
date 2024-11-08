// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.analysis

import com.intellij.lang.Language
import io.vavr.Tuple2
import io.vavr.control.Option
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValue
import org.jetbrains.kotlin.resolve.calls.smartcasts.IdentifierInfo
import org.jetbrains.kotlin.resolve.calls.smartcasts.Nullability
import org.jetbrains.kotlin.types.FlexibleType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.kotlin.types.typeUtil.nullability
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.analysis.UExpressionFact
import org.jetbrains.uast.analysis.UNullability
import org.jetbrains.uast.analysis.UastAnalysisPlugin
import org.jetbrains.uast.kotlin.unwrapBlockOrParenthesis

class KotlinUastAnalysisPlugin : UastAnalysisPlugin {
    override val language: Language
        get() = KotlinLanguage.INSTANCE

    private fun KtReferenceExpression.getVariableDescriptor(context: BindingContext): VariableDescriptor? {
        return context[BindingContext.REFERENCE_TARGET, this] as? VariableDescriptor
    }

    private fun isValueCorrespondsToDescriptor(value: DataFlowValue, descriptor: VariableDescriptor?) =
        when (val identifierInfo = value.identifierInfo) {
            is IdentifierInfo.Variable -> identifierInfo.variable == descriptor
            is IdentifierInfo.Qualified ->
                (identifierInfo.selectorInfo as? IdentifierInfo.Variable)?.let { it.variable == descriptor } == true
            else -> false
        }

    override fun <T : Any> UExpression.getExpressionFact(fact: UExpressionFact<T>): T? {
        val ktElement = (sourcePsi as? KtElement) ?: return null

        @Suppress("UNCHECKED_CAST")
        return when (fact) {
            UExpressionFact.UNullabilityFact -> {
                when (ktElement) {
                    is KtExpression -> getNullabilityForExpression(ktElement)
                    is KtTypeReference -> getNullabilityForTypeReference(ktElement)
                    else -> null
                }
            }
        } as T
    }

    private fun getNullabilityForExpression(psiExpression: KtExpression): UNullability? {
        val ktExpression = psiExpression.unwrapBlockOrParenthesis()

        val typeInfo = ktExpression.analyze()[BindingContext.EXPRESSION_TYPE_INFO, ktExpression]
        val dfaInfo = typeInfo?.dataFlowInfo
        val variableDescriptor = (ktExpression as? KtReferenceExpression)?.getVariableDescriptor(ktExpression.analyze())

        val variableNullability = dfaInfo?.completeNullabilityInfo?.find { (value, _) ->
            isValueCorrespondsToDescriptor(value, variableDescriptor)
        }

        val type = typeInfo?.type ?: return null

        return when {
            type is SimpleType && type.isMarkedNullable == false -> UNullability.NOT_NULL
            variableNullability is Option.Some<*> -> {
                val (_, info) = variableNullability.get()
                when (info) {
                    Nullability.NULL -> UNullability.NULL
                    Nullability.NOT_NULL -> UNullability.NOT_NULL
                    else -> UNullability.UNKNOWN
                }
            }
            else -> getNullabilityForType(type)
        }
    }

    private fun getNullabilityForTypeReference(typeReference: KtTypeReference): UNullability? {
        val type = typeReference.analyze()[BindingContext.TYPE, typeReference] ?: return null

        return getNullabilityForType(type)
    }

    private fun getNullabilityForType(type: KotlinType): UNullability {
        return when {
            type is FlexibleType -> {
                when {
                    !type.lowerBound.isMarkedNullable && type.upperBound.isMarkedNullable -> UNullability.UNKNOWN
                    type.upperBound.isMarkedNullable -> UNullability.NULLABLE
                    else -> UNullability.NOT_NULL
                }
            }

            type.nullability() == TypeNullability.NULLABLE -> UNullability.NULLABLE
            type.nullability() == TypeNullability.NOT_NULL -> UNullability.NOT_NULL
            else -> UNullability.UNKNOWN
        }
    }

    private operator fun <T1, T2> Tuple2<T1, T2>.component1(): T1 = this._1
    private operator fun <T1, T2> Tuple2<T1, T2>.component2(): T2 = this._2
}
