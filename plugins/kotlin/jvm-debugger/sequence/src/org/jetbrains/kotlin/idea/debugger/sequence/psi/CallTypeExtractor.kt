// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.sequence.psi

import com.intellij.debugger.streams.core.trace.impl.handler.type.GenericType
import org.jetbrains.kotlin.idea.core.receiverType
import org.jetbrains.kotlin.idea.core.resolveType
import org.jetbrains.kotlin.idea.debugger.sequence.trace.dsl.KotlinSequenceTypes
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.types.KotlinType

interface CallTypeExtractor {
    fun extractIntermediateCallTypes(call: KtCallExpression): IntermediateCallTypes
    fun extractTerminalCallTypes(call: KtCallExpression): TerminatorCallTypes

    data class IntermediateCallTypes(val typeBefore: GenericType, val typeAfter: GenericType)
    data class TerminatorCallTypes(val typeBefore: GenericType, val resultType: GenericType)

    abstract class Base : CallTypeExtractor {
        override fun extractIntermediateCallTypes(call: KtCallExpression): IntermediateCallTypes =
            IntermediateCallTypes(extractItemsType(call.receiverType()), extractItemsType(call.resolveType()))


        override fun extractTerminalCallTypes(call: KtCallExpression): TerminatorCallTypes =
            TerminatorCallTypes(extractItemsType(call.receiverType()), call.resolveType()?.let {getResultType(it) }  ?: KotlinSequenceTypes.NULLABLE_ANY)


        protected abstract fun extractItemsType(type: KotlinType?): GenericType
        protected abstract fun getResultType(type: KotlinType): GenericType

    }
}