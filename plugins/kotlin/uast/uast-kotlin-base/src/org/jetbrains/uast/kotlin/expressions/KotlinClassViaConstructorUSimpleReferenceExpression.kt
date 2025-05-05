// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.internal.log
import org.jetbrains.uast.visitor.UastVisitor

@ApiStatus.Internal
class KotlinClassViaConstructorUSimpleReferenceExpression(
    override val sourcePsi: KtExpression?,
    override val identifier: String,
    private val resolved: PsiClass,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), USimpleNameReferenceExpression, KotlinUElementWithType {

    override val resolvedName: String?
        get() = (resolved as? PsiNamedElement)?.name

    override fun accept(visitor: UastVisitor) {
        super<USimpleNameReferenceExpression>.accept(visitor)
    }

    override fun resolve(): PsiElement = resolved

    override fun asLogString(): String {
        val resolveStr = when (val resolved = resolve()) {
            is PsiClass -> "PsiClass: ${resolved.name}"
            is PsiMethod -> "PsiMethod: ${resolved.name}"
            else -> resolved.toString()
        }
        return log<USimpleNameReferenceExpression>("identifier = $identifier, resolvesTo = $resolveStr")
    }
}
