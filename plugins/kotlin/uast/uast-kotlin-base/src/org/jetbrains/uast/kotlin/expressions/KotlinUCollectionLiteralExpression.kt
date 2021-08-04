// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin.expressions

import org.jetbrains.uast.UElement
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression

@Deprecated(
    "provided for BWC",
    replaceWith = ReplaceWith("KotlinUCollectionLiteralExpression", imports = arrayOf("org.jetbrains.uast.kotlin.KotlinUCollectionLiteralExpression")),
    level = DeprecationLevel.ERROR
)
class KotlinUCollectionLiteralExpression(
    sourcePsi: KtCollectionLiteralExpression,
    givenParent: UElement?
): org.jetbrains.uast.kotlin.KotlinUCollectionLiteralExpression(
    sourcePsi, givenParent
)