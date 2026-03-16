// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastEmptyExpression
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.convertOpt
import org.jetbrains.uast.getOrBuild

@ApiStatus.Internal
class KotlinLazyUBlockExpression(
    override val uastParent: UElement?,
    private val expressionProducer: (expressionParent: UElement) -> List<UExpression>
) : UBlockExpression {

    private val expressionsPart = UastLazyPart<List<UExpression>>()

    override val psi: PsiElement? get() = null
    override val javaPsi: PsiElement? get() = null
    override val sourcePsi: PsiElement? get() = null
    override val uAnnotations: List<UAnnotation> = emptyList()
    override val expressions: List<UExpression>
        get() = expressionsPart.getOrBuild { expressionProducer(this) }

    companion object {
        fun create(initializers: List<KtAnonymousInitializer>, uastParent: UElement): UBlockExpression {
            val languagePlugin = UastFacade.findPlugin(uastParent.lang)
            return KotlinLazyUBlockExpression(uastParent) { expressionParent ->
                initializers.map {
                    languagePlugin?.convertOpt(it.body, expressionParent) ?: UastEmptyExpression(expressionParent)
                }
            }
        }
    }
}
