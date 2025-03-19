// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinUTryExpression(
    override val sourcePsi: KtTryExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UTryExpression, KotlinUElementWithType {

    private val tryClausePart = UastLazyPart<UExpression>()
    private val catchClausesPart = UastLazyPart<List<KotlinUCatchClause>>()
    private val finallyClausePart = UastLazyPart<UExpression?>()

    override val tryClause: UExpression
        get() = tryClausePart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.tryBlock, this)
        }

    override val catchClauses: List<KotlinUCatchClause>
        get() = catchClausesPart.getOrBuild {
            sourcePsi.catchClauses.map { KotlinUCatchClause(it, this) }
        }

    override val finallyClause: UExpression?
        get() = finallyClausePart.getOrBuild {
            sourcePsi.finallyBlock?.finalExpression?.let {
                baseResolveProviderService.baseKotlinConverter.convertExpression(it, this, DEFAULT_EXPRESSION_TYPES_LIST)
            }
        }

    @Deprecated("This API doesn't support resource expression", replaceWith = ReplaceWith("resources"))
    override val resourceVariables: List<UVariable> get() = resources.filterIsInstance<UVariable>()

    override val resources: List<UAnnotated>
        get() = emptyList()

    override val hasResources: Boolean
        get() = false

    override val tryIdentifier: UIdentifier
        get() = KotlinUIdentifier(null, this)

    override val finallyIdentifier: UIdentifier?
        get() = null
}
