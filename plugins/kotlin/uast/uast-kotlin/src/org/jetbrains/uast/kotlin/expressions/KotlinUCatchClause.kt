// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter
import org.jetbrains.uast.visitor.UastVisitor

class KotlinUCatchClause(
        override val sourcePsi: KtCatchClause,
        givenParent: UElement?
) : KotlinAbstractUElement(givenParent), UCatchClause {
    override val psi: PsiElement?
        get() = sourcePsi

    override val javaPsi: PsiElement? get() = null

    override val body by lz { KotlinConverter.convertOrEmpty(sourcePsi.catchBody, this) }

    override val parameters by lz {
        val parameter = sourcePsi.catchParameter ?: return@lz emptyList<UParameter>()
        listOf(KotlinUParameter(UastKotlinPsiParameter.create(parameter, sourcePsi, this, 0), parameter, this))
    }

    override val typeReferences by lz {
        val parameter = sourcePsi.catchParameter ?: return@lz emptyList<UTypeReferenceExpression>()
        val typeReference = parameter.typeReference ?: return@lz emptyList<UTypeReferenceExpression>()
        listOf(KotlinUTypeReferenceExpression(typeReference, this, baseResolveProviderService) { typeReference.toPsiType(this, boxed = true) })
    }

    // equal to IDEA 202 implementation
    override fun accept(visitor: UastVisitor) {
        if (visitor.visitCatchClause(this)) return
        parameters.acceptList(visitor)
        body.accept(visitor)
        visitor.afterVisitCatchClause(this)
    }

    // equal to IDEA 202 implementation
    override fun asRenderString(): String = "catch (${parameters.joinToString { it.asRenderString() }}) ${body.asRenderString()}"

}
