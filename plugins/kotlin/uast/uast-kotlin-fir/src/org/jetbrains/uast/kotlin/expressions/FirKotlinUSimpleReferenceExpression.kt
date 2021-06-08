// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.uast.UElement

class FirKotlinUSimpleReferenceExpression(
    override val sourcePsi: KtSimpleNameExpression,
    givenParent: UElement?
) : KotlinAbstractUSimpleReferenceExpression(sourcePsi, givenParent) {
    // TODO: handle destructuring declaration differently or commonize it
}
