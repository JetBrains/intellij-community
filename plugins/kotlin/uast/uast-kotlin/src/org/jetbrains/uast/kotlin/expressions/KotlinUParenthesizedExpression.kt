// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParenthesizedExpression

class KotlinUParenthesizedExpression(
        override val sourcePsi: KtParenthesizedExpression,
        givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UParenthesizedExpression, KotlinUElementWithType {
    override val expression by lz { KotlinConverter.convertOrEmpty(sourcePsi.expression, this) }
}