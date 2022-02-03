// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.expressions

import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.kotlin.KotlinAbstractUArrayAccessExpression

class FirKotlinUArrayAccessExpression(
    override val sourcePsi: KtArrayAccessExpression,
    givenParent: UElement?
) : KotlinAbstractUArrayAccessExpression(sourcePsi, givenParent)
