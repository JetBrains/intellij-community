// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.uast.*

class KotlinLazyUBlockExpression(
    override val uastParent: UElement?,
    expressionProducer: (expressionParent: UElement) -> List<UExpression>
) : UBlockExpression {
    override val psi: PsiElement? get() = null
    override val javaPsi: PsiElement? get() = null
    override val sourcePsi: PsiElement? get() = null
    override val uAnnotations: List<UAnnotation> = emptyList()
    override val expressions by lz { expressionProducer(this) }

    companion object {
        fun create(initializers: List<KtAnonymousInitializer>, uastParent: UElement): UBlockExpression {
            val languagePlugin = uastParent.getLanguagePlugin()
            return KotlinLazyUBlockExpression(uastParent) { expressionParent ->
                initializers.map {
                    languagePlugin.convertOpt(it.body, expressionParent) ?: UastEmptyExpression(expressionParent)
                }
            }
        }
    }
}
