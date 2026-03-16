// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.mutability

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.BoundTypeLabel
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ConstraintBound
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ConstraintBoundProviderImpl
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.GenericLabel
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.LiteralLabel
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.NullLiteralLabel
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.StarProjectionLabel
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.TypeParameterLabel
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.TypeVariableLabel

@K1Deprecation
class MutabilityConstraintBoundProvider : ConstraintBoundProviderImpl() {
    override fun BoundTypeLabel.constraintBound(): ConstraintBound? = when (this) {
        is TypeVariableLabel -> typeVariable.constraintBound()
        is TypeParameterLabel -> null
        is GenericLabel -> null
        StarProjectionLabel -> null
        NullLiteralLabel -> null
        LiteralLabel -> null
    }
}