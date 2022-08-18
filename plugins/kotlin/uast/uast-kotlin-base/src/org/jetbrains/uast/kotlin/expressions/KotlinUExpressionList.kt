// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UastSpecialExpressionKind
import org.jetbrains.uast.kotlin.kinds.KotlinSpecialExpressionKinds

@ApiStatus.Internal
open class KotlinUExpressionList(
    override val sourcePsi: PsiElement?,
    override val kind: UastSpecialExpressionKind, // original element
    givenParent: UElement?,
) : KotlinAbstractUExpression(givenParent), UExpressionList, KotlinUElementWithType, KotlinEvaluatableUElement {
    override lateinit var expressions: List<UExpression>

    companion object {
        fun createClassBody(psi: PsiElement?, uastParent: UElement?): KotlinUExpressionList =
            KotlinUExpressionList(psi, KotlinSpecialExpressionKinds.CLASS_BODY, uastParent).apply {
                expressions = emptyList()
            }
    }
}
