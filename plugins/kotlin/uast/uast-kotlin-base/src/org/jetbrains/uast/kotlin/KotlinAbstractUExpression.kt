// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression

@ApiStatus.Internal
abstract class KotlinAbstractUExpression(
    givenParent: UElement?,
) : KotlinAbstractUElement(givenParent), UExpression {

    override val javaPsi: PsiElement? = null

    override val psi
        get() = sourcePsi

    override val uAnnotations: List<UAnnotation>
        get() {
            val annotatedExpression = sourcePsi?.parent as? KtAnnotatedExpression ?: return emptyList()
            return annotatedExpression.annotationEntries.map {
                baseResolveProviderService.baseKotlinConverter.convertAnnotation(it, this)
            }
        }
}
