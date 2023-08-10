// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.nullability

import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isNullExpression
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.BoundTypeCalculator
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ConstraintBuilder
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.InferenceContext
import org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.collectors.ConstraintsCollector
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.*

class NullabilityConstraintsCollector : ConstraintsCollector() {
    override fun ConstraintBuilder.collectConstraints(
        element: KtElement,
        boundTypeCalculator: BoundTypeCalculator,
        inferenceContext: InferenceContext,
        resolutionFacade: ResolutionFacade
    ) {
        when {
            element is KtBinaryExpression &&
                    (element.left?.isNullExpression() == true
                            || element.right?.isNullExpression() == true) -> {
                val notNullOperand =
                    if (element.left?.isNullExpression() == true) element.right
                    else element.left
                notNullOperand?.isTheSameTypeAs(
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.State.UPPER,
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ConstraintPriority.COMPARE_WITH_NULL
                )
            }

            element is KtQualifiedExpression -> {
                element.receiverExpression.isTheSameTypeAs(
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.State.LOWER,
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ConstraintPriority.USE_AS_RECEIVER
                )
            }

            element is KtForExpression -> {
                element.loopRange?.isTheSameTypeAs(
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.State.LOWER,
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ConstraintPriority.USE_AS_RECEIVER
                )
            }

            element is KtWhileExpressionBase -> {
                element.condition?.isTheSameTypeAs(
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.State.LOWER,
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ConstraintPriority.USE_AS_RECEIVER
                )
            }

            element is KtIfExpression -> {
                element.condition?.isTheSameTypeAs(
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.State.LOWER,
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ConstraintPriority.USE_AS_RECEIVER
                )
            }

            element is KtValueArgument && element.isSpread -> {
                element.getArgumentExpression()?.isTheSameTypeAs(
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.State.LOWER,
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ConstraintPriority.USE_AS_RECEIVER
                )
            }

            element is KtBinaryExpression && !KtPsiUtil.isAssignment(element) -> {
                element.left?.isTheSameTypeAs(
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.State.LOWER,
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ConstraintPriority.USE_AS_RECEIVER
                )
                element.right?.isTheSameTypeAs(
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.State.LOWER,
                    org.jetbrains.kotlin.idea.j2k.post.processing.inference.common.ConstraintPriority.USE_AS_RECEIVER
                )
            }
        }
    }
}