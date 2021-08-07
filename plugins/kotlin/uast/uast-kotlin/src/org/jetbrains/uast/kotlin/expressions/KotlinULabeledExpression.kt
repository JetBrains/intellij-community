// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.ULabeledExpression
import org.jetbrains.uast.kotlin.declarations.KotlinUIdentifier

class KotlinULabeledExpression(
        override val sourcePsi: KtLabeledExpression,
        givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), ULabeledExpression {
    override val label: String
        get() = sourcePsi.getLabelName().orAnonymous("label")

    override val labelIdentifier: UIdentifier?
        get() = sourcePsi.getTargetLabel()?.let { KotlinUIdentifier(it, this) }

    override val expression by lz { KotlinConverter.convertOrEmpty(sourcePsi.baseExpression, this) }
}