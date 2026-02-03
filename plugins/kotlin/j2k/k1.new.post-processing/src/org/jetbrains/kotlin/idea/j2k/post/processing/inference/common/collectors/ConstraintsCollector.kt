// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.collectors

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.BoundTypeCalculator
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ConstraintBuilder
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.InferenceContext
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.KtElement

@K1Deprecation
abstract class ConstraintsCollector {
    abstract fun ConstraintBuilder.collectConstraints(
        element: KtElement,
        boundTypeCalculator: BoundTypeCalculator,
        inferenceContext: InferenceContext,
        resolutionFacade: ResolutionFacade
    )
}