// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve

@ApiStatus.Internal
class KotlinUSuperExpression(
    override val sourcePsi: KtSuperExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), USuperExpression, DelegatedMultiResolve, KotlinUElementWithType, KotlinEvaluatableUElement {
    override val label: String?
        get() = sourcePsi.getLabelName()

    override val labelIdentifier: UIdentifier?
        get() = sourcePsi.getTargetLabel()?.let { KotlinUIdentifier(it, this) }

    override fun resolve() =
        baseResolveProviderService.resolveToDeclaration(sourcePsi)
}
