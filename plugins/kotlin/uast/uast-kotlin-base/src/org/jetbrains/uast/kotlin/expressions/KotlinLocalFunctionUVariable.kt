// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiVariable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UVariableEx
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiVariable

@ApiStatus.Internal
class KotlinLocalFunctionUVariable(
    val function: KtFunction,
    override val javaPsi: PsiVariable,
    givenParent: UElement?
) : KotlinAbstractUElement(givenParent), UVariableEx, PsiVariable by javaPsi {

    private val uastInitializerPart = UastLazyPart<UExpression?>()

    override val psi: PsiVariable get() = javaPsi
    override val sourcePsi: PsiElement = (javaPsi as? UastKotlinPsiVariable?)?.ktElement ?: javaPsi

    override val uastInitializer: UExpression?
        get() = uastInitializerPart.getOrBuild {
            createLocalFunctionLambdaExpression(function, this)
        }

    override val typeReference: UTypeReferenceExpression? = null
    override val uastAnchor: UElement? = null
    override val uAnnotations: List<UAnnotation> = emptyList()
    override fun getOriginalElement(): PsiElement {
        return psi.originalElement
    }

    override fun getInitializer(): PsiExpression? {
        return psi.initializer
    }
}

