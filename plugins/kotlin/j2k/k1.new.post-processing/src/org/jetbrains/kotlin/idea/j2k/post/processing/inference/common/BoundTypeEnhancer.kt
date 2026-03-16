// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.types.KotlinType

@K1Deprecation
abstract class BoundTypeEnhancer {
    abstract fun enhance(
        expression: KtExpression,
        boundType: BoundType,
        inferenceContext: InferenceContext
    ): BoundType

    abstract fun enhanceKotlinType(
        type: KotlinType,
        boundType: BoundType,
        allowLowerEnhancement: Boolean,
        inferenceContext: InferenceContext
    ): BoundType

    object ID : BoundTypeEnhancer() {
        override fun enhance(
            expression: KtExpression,
            boundType: BoundType,
            inferenceContext: InferenceContext
        ): BoundType = boundType

        override fun enhanceKotlinType(
            type: KotlinType,
            boundType: BoundType,
            allowLowerEnhancement: Boolean,
            inferenceContext: InferenceContext
        ): BoundType = boundType
    }
}