// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.nullability

import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.*

class NullabilityConstraintBoundProvider : ConstraintBoundProviderImpl() {
    override fun BoundTypeLabel.constraintBound(): ConstraintBound? = when (this) {
        is TypeVariableLabel -> typeVariable.constraintBound()
        is TypeParameterLabel -> null
        is GenericLabel -> null
        StarProjectionLabel -> null
        NullLiteralLabel -> org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.LiteralBound.UPPER
        LiteralLabel -> org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.LiteralBound.LOWER
    }
}