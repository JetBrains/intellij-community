// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast

import com.intellij.psi.PsiElement
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.BaseResolveProviderServiceSupplier
import org.jetbrains.uast.kotlin.KotlinAbstractUExpression

open class KotlinUDeclarationsExpression(
    override val psi: PsiElement?,
    givenParent: UElement?,
    override val baseResolveProviderService: BaseKotlinUastResolveProviderService,
    val psiAnchor: PsiElement? = null,
    baseResolveProviderServiceSupplier: BaseResolveProviderServiceSupplier? = null,
) : KotlinAbstractUExpression(givenParent, baseResolveProviderServiceSupplier), UDeclarationsExpression {

    override val sourcePsi: PsiElement?
        get() = psiAnchor

    override fun convertParent(): UElement? =
            psiAnchor?.let { baseResolveProviderService.convertParent(this, it.parent) } ?: super.convertParent()

    constructor(uastParent: UElement?, baseResolveProviderService: BaseKotlinUastResolveProviderService) :
            this(null, uastParent, baseResolveProviderService)

    override lateinit var declarations: List<UDeclaration>
}
