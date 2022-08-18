// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.uast.UContinueExpression
import org.jetbrains.uast.UElement

@ApiStatus.Internal
class KotlinUContinueExpression(
    override val sourcePsi: KtContinueExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UContinueExpression {
    override val label: String?
        get() = sourcePsi.getLabelName()
}
