/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.uast.UElement
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastVisitor

class KotlinClassViaConstructorUSimpleReferenceExpression(
    override val sourcePsi: KtCallElement,
    override val identifier: String,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), USimpleNameReferenceExpression, KotlinUElementWithType {
    override val resolvedName: String?
        get() = (resolved as? PsiNamedElement)?.name

    private val resolved by lz {
        baseResolveProviderService.resolveToClassIfConstructorCall(sourcePsi, this)
    }

    override fun accept(visitor: UastVisitor) {
        super<KotlinAbstractUExpression>.accept(visitor)
    }

    override fun resolve(): PsiElement? = resolved

    override fun asLogString(): String {
        val resolveStr = when (val resolved = resolve()) {
            is PsiClass -> "PsiClass: ${resolved.name}"
            is PsiMethod -> "PsiMethod: ${resolved.name}"
            else -> resolved.toString()
        }
        return log<USimpleNameReferenceExpression>("identifier = $identifier, resolvesTo = $resolveStr")
    }
}
