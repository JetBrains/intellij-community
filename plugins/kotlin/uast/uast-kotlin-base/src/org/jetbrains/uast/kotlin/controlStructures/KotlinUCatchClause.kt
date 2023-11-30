// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter
import org.jetbrains.uast.visitor.UastVisitor

@ApiStatus.Internal
class KotlinUCatchClause(
    override val sourcePsi: KtCatchClause,
    givenParent: UElement?
) : KotlinAbstractUElement(givenParent), UCatchClause {

    private val bodyPart = UastLazyPart<UExpression>()
    private val parametersPart = UastLazyPart<List<UParameter>>()
    private val typeReferencesPart = UastLazyPart<List<UTypeReferenceExpression>>()

    override val psi: PsiElement
        get() = sourcePsi

    override val javaPsi: PsiElement? get() = null

    override val body: UExpression
        get() = bodyPart.getOrBuild {
            baseResolveProviderService.baseKotlinConverter.convertOrEmpty(sourcePsi.catchBody, this)
        }

    override val parameters: List<UParameter>
        get() = parametersPart.getOrBuild {
            val parameter = sourcePsi.catchParameter ?: return@getOrBuild emptyList<UParameter>()
            listOf(
                KotlinUParameter(UastKotlinPsiParameter.create(parameter, sourcePsi, this, 0), parameter, this)
            )
        }

    override val typeReferences: List<UTypeReferenceExpression>
        get() = typeReferencesPart.getOrBuild {
            val parameter = sourcePsi.catchParameter ?: return@getOrBuild emptyList<UTypeReferenceExpression>()
            val typeReference = parameter.typeReference ?: return@getOrBuild emptyList<UTypeReferenceExpression>()
            listOf(
                KotlinUTypeReferenceExpression(typeReference, this) {
                    baseResolveProviderService.resolveToType(typeReference, this, isBoxed = true) ?: UastErrorType
                }
            )
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
