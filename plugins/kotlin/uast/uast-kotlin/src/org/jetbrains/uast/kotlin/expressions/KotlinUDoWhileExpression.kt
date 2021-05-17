// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.kotlin.psi.KtDoWhileExpression
import org.jetbrains.uast.UDoWhileExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIdentifier

class KotlinUDoWhileExpression(
        override val sourcePsi: KtDoWhileExpression,
        givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UDoWhileExpression {
    override val condition by lz { KotlinConverter.convertOrEmpty(sourcePsi.condition, this) }
    override val body by lz { KotlinConverter.convertOrEmpty(sourcePsi.body, this) }

    override val doIdentifier: UIdentifier
        get() = KotlinUIdentifier(null, this)

    override val whileIdentifier: UIdentifier
        get() = KotlinUIdentifier(null, this)
}
