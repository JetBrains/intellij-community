// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.uast.UBreakExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULabeledExpression
import org.jetbrains.uast.ULoopExpression
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild

@ApiStatus.Internal
class KotlinUBreakExpression(
    override val sourcePsi: KtBreakExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UBreakExpression {
    private val jumpTargetPart = UastLazyPart<UElement?>()

    override val label: String?
        get() = sourcePsi.getLabelName()

    override val jumpTarget: UElement?
        get() = jumpTargetPart.getOrBuild {
            generateSequence(uastParent) { it.uastParent }
                .firstNotNullOfOrNull {
                    when (it) {
                        is ULabeledExpression if it.label == label -> it.expression
                        is ULoopExpression if label == null -> it
                        else -> null
                    }
                }
        }
}
