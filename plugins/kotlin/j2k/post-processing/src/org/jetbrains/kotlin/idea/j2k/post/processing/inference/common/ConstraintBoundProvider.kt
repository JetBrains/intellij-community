// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common

interface ConstraintBoundProvider {
    fun TypeVariable.constraintBound(): TypeVariableBound
    fun BoundType.constraintBound(): ConstraintBound?
    fun BoundTypeLabel.constraintBound(): ConstraintBound?
}

abstract class ConstraintBoundProviderImpl : ConstraintBoundProvider {
    final override fun TypeVariable.constraintBound(): TypeVariableBound =
        TypeVariableBound(this)

    final override fun BoundType.constraintBound(): ConstraintBound? = when (this) {
        is BoundTypeImpl -> label.constraintBound()
        is WithForcedStateBoundType -> forcedState.constraintBound()
    }
}