// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UThrowExpression

class KotlinUThrowExpression(
        override val sourcePsi: KtThrowExpression,
        givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UThrowExpression, KotlinUElementWithType {
    override val thrownExpression by lz { KotlinConverter.convertOrEmpty(sourcePsi.thrownExpression, this) }
}