// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin.expressions

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.uast.DEFAULT_TYPES_LIST
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.expressions.UTypeArgumentList
import org.jetbrains.uast.kotlin.KotlinAbstractUElement
import org.jetbrains.uast.kotlin.KotlinConverter
import org.jetbrains.uast.kotlin.lz

class KotlinUTypeArgumentList(
    override val sourcePsi: KtTypeArgumentList,
    givenParent: UElement?,
) : KotlinAbstractUElement(givenParent), UTypeArgumentList {
    override val psi: PsiElement get() = sourcePsi

    override val arguments: List<UTypeReferenceExpression> by lz {
        sourcePsi.arguments.map {
            KotlinConverter.convertPsiElement(it, this, DEFAULT_TYPES_LIST) as UTypeReferenceExpression
        }
    }
}