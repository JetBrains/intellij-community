// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.inference.nullability

import org.jetbrains.kotlin.nj2k.inference.common.*

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