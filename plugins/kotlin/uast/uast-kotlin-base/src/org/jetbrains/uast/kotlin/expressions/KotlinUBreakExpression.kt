// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.uast.UBreakExpression
import org.jetbrains.uast.UElement

@ApiStatus.Internal
class KotlinUBreakExpression(
    override val sourcePsi: KtBreakExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UBreakExpression {
    override val label: String?
        get() = sourcePsi.getLabelName()
}
