// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinUClassLiteralExpression(
    override val sourcePsi: KtClassLiteralExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UClassLiteralExpression, KotlinUElementWithType {

    private val typePart = UastLazyPart<PsiType?>()

    override val type: PsiType?
        get() = typePart.getOrBuild {
            baseResolveProviderService.getDoubleColonReceiverType(sourcePsi, this)
        }

    override val expression: UExpression?
        get() {
            if (type != null) return null
            val receiverExpression = sourcePsi.receiverExpression ?: return null
            return baseResolveProviderService.baseKotlinConverter.convertExpression(receiverExpression, this, DEFAULT_EXPRESSION_TYPES_LIST)
        }
}
