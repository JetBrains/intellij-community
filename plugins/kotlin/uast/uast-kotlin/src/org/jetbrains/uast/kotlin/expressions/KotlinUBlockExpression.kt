// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.uast.*

open class KotlinUBlockExpression(
    override val sourcePsi: KtBlockExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UBlockExpression, KotlinUElementWithType {
    override val expressions by lz { sourcePsi.statements.map { KotlinConverter.convertOrEmpty(it, this) } }

    class KotlinLazyUBlockExpression(
            override val uastParent: UElement?,
            expressionProducer: (expressionParent: UElement) -> List<UExpression>
    ) : UBlockExpression {
        override val psi: PsiElement? get() = null
        override val javaPsi: PsiElement? get() = null
        override val sourcePsi: PsiElement? get() = null
        override val uAnnotations: List<UAnnotation> = emptyList()
        override val expressions by lz { expressionProducer(this) }
    }

    companion object {
        fun create(initializers: List<KtAnonymousInitializer>, uastParent: UElement): UBlockExpression {
            val languagePlugin = uastParent.getLanguagePlugin()
            return KotlinLazyUBlockExpression(uastParent) { expressionParent ->
                initializers.map { languagePlugin.convertOpt<UExpression>(it.body, expressionParent) ?: UastEmptyExpression(expressionParent) }
            }
        }
    }

    override fun convertParent(): UElement? {
        val directParent = super.convertParent()
        if (directParent is UnknownKotlinExpression && directParent.sourcePsi is KtAnonymousInitializer) {
            val containingUClass = directParent.getContainingUClass() ?: return directParent
            containingUClass.methods
                    .find { it is KotlinConstructorUMethod && it.isPrimary || it is KotlinSecondaryConstructorWithInitializersUMethod }?.let {
                return it.uastBody
            }
        }
        return directParent
    }
}
