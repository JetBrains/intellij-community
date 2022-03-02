// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UForEachExpression
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter
import org.jetbrains.uast.psi.UastPsiParameterNotResolved

class KotlinUForEachExpression(
    override val sourcePsi: KtForExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UForEachExpression {
    override val iteratedValue by lz {
        baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.loopRange, this)
    }
    override val body by lz {
        baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.body, this)
    }

    override val variable by lz {
        val parameter = sourcePsi.loopParameter?.let { UastKotlinPsiParameter.create(it, sourcePsi, this, 0) }
                ?: UastPsiParameterNotResolved(sourcePsi, KotlinLanguage.INSTANCE)
        KotlinUParameter(parameter, sourcePsi, this)
    }

    override val forIdentifier: UIdentifier
        get() = KotlinUIdentifier(null, this)
}
