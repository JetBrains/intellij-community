// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import java.util.Collections

interface OutputValue<KotlinType> {
    val originalExpressions: List<KtExpression>
    val valueType: KotlinType

    class ExpressionValue<KotlinType>(
        val callSiteReturn: Boolean,
        val hasImplicitReturn: Boolean,
        override val originalExpressions: List<KtExpression>,
        override val valueType: KotlinType
    ) : OutputValue<KotlinType>

    class Jump<KotlinType>(
        val elementsToReplace: List<KtExpression>,
        val elementToInsertAfterCall: KtElement?,
        val conditional: Boolean,
        private val booleanType: KotlinType,
        private val unitType: KotlinType
    ) : OutputValue<KotlinType> {
        override val originalExpressions: List<KtExpression> get() = elementsToReplace
        override val valueType: KotlinType = if (conditional) booleanType else unitType
    }

    class ParameterUpdate<KotlinType>(
        val parameter: IParameter<KotlinType>,
        override val originalExpressions: List<KtExpression>
    ) : OutputValue<KotlinType> {
        override val valueType: KotlinType get() = parameter.parameterType
    }

    class Initializer<KotlinType>(
        val initializedDeclaration: KtProperty,
        override val valueType: KotlinType
    ) : OutputValue<KotlinType> {
        override val originalExpressions: List<KtExpression> get() = Collections.singletonList(initializedDeclaration)
    }
}
