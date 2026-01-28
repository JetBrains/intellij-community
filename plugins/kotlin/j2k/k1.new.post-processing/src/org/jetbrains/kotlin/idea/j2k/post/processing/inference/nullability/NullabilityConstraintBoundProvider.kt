// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.nullability

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.*

@K1Deprecation
class NullabilityConstraintBoundProvider : ConstraintBoundProviderImpl() {
    override fun BoundTypeLabel.constraintBound(): ConstraintBound? = when (this) {
        is TypeVariableLabel -> typeVariable.constraintBound()
        is TypeParameterLabel -> null
        is GenericLabel -> null
        StarProjectionLabel -> null
        NullLiteralLabel -> LiteralBound.UPPER
        LiteralLabel -> LiteralBound.LOWER
    }
}