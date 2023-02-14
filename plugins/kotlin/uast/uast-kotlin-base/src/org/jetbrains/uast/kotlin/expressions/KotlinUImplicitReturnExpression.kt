// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.kotlin.internal.KotlinFakeUElement

@ApiStatus.Internal
class KotlinUImplicitReturnExpression(
    givenParent: UElement?,
) : KotlinAbstractUExpression(givenParent), UReturnExpression, KotlinUElementWithType, KotlinFakeUElement {
    override val psi: PsiElement?
        get() = null

    override lateinit var returnExpression: UExpression

    override fun unwrapToSourcePsi(): List<PsiElement> {
        return returnExpression.toSourcePsiFakeAware()
    }
}
