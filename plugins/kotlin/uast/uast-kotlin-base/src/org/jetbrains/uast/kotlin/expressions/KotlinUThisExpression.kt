// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve

@ApiStatus.Internal
class KotlinUThisExpression(
    override val sourcePsi: KtThisExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UThisExpression, DelegatedMultiResolve, KotlinUElementWithType, KotlinEvaluatableUElement {
    override val label: String?
        get() = sourcePsi.getLabelName()

    override val labelIdentifier: UIdentifier?
        get() = sourcePsi.getTargetLabel()?.let { KotlinUIdentifier(it, this) }

    override fun resolve() =
        baseResolveProviderService.resolveToDeclaration(sourcePsi)
}
