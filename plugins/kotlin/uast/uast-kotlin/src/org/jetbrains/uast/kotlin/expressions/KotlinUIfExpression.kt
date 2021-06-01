// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UIfExpression

class KotlinUIfExpression(
        override val sourcePsi: KtIfExpression,
        givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UIfExpression, KotlinUElementWithType, KotlinEvaluatableUElement {
    override val condition by lz { KotlinConverter.convertOrEmpty(sourcePsi.condition, this) }
    override val thenExpression by lz { KotlinConverter.convertOrNull(sourcePsi.then, this) }
    override val elseExpression by lz { KotlinConverter.convertOrNull(sourcePsi.`else`, this) }
    override val isTernary = false

    override val ifIdentifier: UIdentifier
        get() = UIdentifier(null, this)

    override val elseIdentifier: UIdentifier?
        get() = null
}