// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.uast.*

class KotlinUTryExpression(
    override val sourcePsi: KtTryExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UTryExpression, KotlinUElementWithType {
    override val tryClause by lz {
        baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.tryBlock, this)
    }
    override val catchClauses by lz {
        sourcePsi.catchClauses.map { KotlinUCatchClause(it, this) }
    }
    override val finallyClause by lz {
        sourcePsi.finallyBlock?.finalExpression?.let {
            baseResolveProviderService.baseKotlinConverter.convertExpression(it, this, DEFAULT_EXPRESSION_TYPES_LIST)
        }
    }

    override val resourceVariables: List<UVariable>
        get() = emptyList()

    override val hasResources: Boolean
        get() = false

    override val tryIdentifier: UIdentifier
        get() = KotlinUIdentifier(null, this)

    override val finallyIdentifier: UIdentifier?
        get() = null
}
