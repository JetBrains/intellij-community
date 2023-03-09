// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common

import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.collectors.ConstraintsCollector
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class ConstraintsCollectorAggregator(
    private val resolutionFacade: ResolutionFacade,
    private val constraintBoundProvider: ConstraintBoundProvider,
    val collectors: List<ConstraintsCollector>
) {
    fun collectConstraints(
        boundTypeCalculator: BoundTypeCalculator,
        inferenceContext: InferenceContext,
        elements: List<KtElement>
    ): List<Constraint> {
        val constraintsBuilder = ConstraintBuilder(inferenceContext, boundTypeCalculator, constraintBoundProvider)
        for (element in elements) {
            element.forEachDescendantOfType<KtElement> { innerElement ->
                if (innerElement.getStrictParentOfType<KtImportDirective>() != null) return@forEachDescendantOfType
                for (collector in collectors) {
                    with(collector) {
                        constraintsBuilder.collectConstraints(
                            innerElement,
                            boundTypeCalculator,
                            inferenceContext,
                            resolutionFacade
                        )
                    }
                }
            }
        }
        return constraintsBuilder.collectedConstraints
    }
}
