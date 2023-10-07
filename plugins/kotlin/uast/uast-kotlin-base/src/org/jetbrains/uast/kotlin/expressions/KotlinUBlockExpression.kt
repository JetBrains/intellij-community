// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.uast.*

@ApiStatus.Internal
open class KotlinUBlockExpression(
    override val sourcePsi: KtBlockExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UBlockExpression, KotlinUElementWithType {
    private val expressionsPart = UastLazyPart<List<UExpression>>()

    override val expressions: List<UExpression>
        get() = expressionsPart.getOrBuild {
            sourcePsi.statements.map { baseResolveProviderService.baseKotlinConverter.convertOrEmpty(it, this) }
        }

    override fun convertParent(): UElement? {
        val directParent = super.convertParent()
        if (directParent is UnknownKotlinExpression && directParent.sourcePsi is KtAnonymousInitializer) {
            val containingUClass = directParent.getContainingUClass() ?: return directParent
            containingUClass.methods.find {
                it is KotlinConstructorUMethod && it.isPrimary || it is KotlinSecondaryConstructorWithInitializersUMethod
            }?.let {
                return it.uastBody
            }
        }
        return directParent
    }
}
