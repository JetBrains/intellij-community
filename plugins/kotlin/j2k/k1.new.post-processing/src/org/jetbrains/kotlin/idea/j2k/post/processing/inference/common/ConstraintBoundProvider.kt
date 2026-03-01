// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common

import org.jetbrains.kotlin.K1Deprecation

@K1Deprecation
interface ConstraintBoundProvider {
    fun TypeVariable.constraintBound(): TypeVariableBound
    fun BoundType.constraintBound(): ConstraintBound?
    fun BoundTypeLabel.constraintBound(): ConstraintBound?
}

@K1Deprecation
abstract class ConstraintBoundProviderImpl : ConstraintBoundProvider {
    final override fun TypeVariable.constraintBound(): TypeVariableBound =
        TypeVariableBound(this)

    final override fun BoundType.constraintBound(): ConstraintBound? = when (this) {
        is BoundTypeImpl -> label.constraintBound()
        is WithForcedStateBoundType -> forcedState.constraintBound()
    }
}