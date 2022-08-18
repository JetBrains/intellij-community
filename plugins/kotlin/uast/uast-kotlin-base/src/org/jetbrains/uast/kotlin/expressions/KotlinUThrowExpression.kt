// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UThrowExpression

@ApiStatus.Internal
class KotlinUThrowExpression(
    override val sourcePsi: KtThrowExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UThrowExpression, KotlinUElementWithType {
    override val thrownExpression by lz {
        baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.thrownExpression, this)
    }
}
