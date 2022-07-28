// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UastErrorType

@ApiStatus.Internal
class KotlinUTypeReferenceExpression(
    override val sourcePsi: KtTypeReference?,
    givenParent: UElement?,
    private val typeSupplier: (() -> PsiType)? = null
) : KotlinAbstractUExpression(givenParent), UTypeReferenceExpression, KotlinUElementWithType {
    override val type: PsiType by lz {
        typeSupplier?.invoke()
            ?: sourcePsi?.let { baseResolveProviderService.resolveToType(it, uastParent ?: this, boxed = false) }
            ?: UastErrorType
    }
}
