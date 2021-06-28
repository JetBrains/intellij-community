// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin.expressions

import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.kotlin.KotlinAbstractUExpression

abstract class KotlinLocalFunctionULambdaExpression(
    override val sourcePsi: KtFunction,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), ULambdaExpression