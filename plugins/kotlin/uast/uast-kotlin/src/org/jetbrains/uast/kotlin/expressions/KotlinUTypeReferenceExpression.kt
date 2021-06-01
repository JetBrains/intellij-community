// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiType
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UTypeReferenceExpression

class KotlinUTypeReferenceExpression(
    override val sourcePsi: KtTypeReference?,
    givenParent: UElement?,
    private val typeSupplier: (() -> PsiType)? = null
) : KotlinAbstractUExpression(givenParent), UTypeReferenceExpression, KotlinUElementWithType {
    override val type: PsiType by lz {
        typeSupplier?.invoke() ?: sourcePsi.toPsiType(uastParent ?: this)
    }
}
