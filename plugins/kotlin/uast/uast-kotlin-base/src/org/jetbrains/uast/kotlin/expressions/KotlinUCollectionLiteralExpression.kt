// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.uast.*
import org.jetbrains.uast.kotlin.internal.DelegatedMultiResolve

@ApiStatus.Internal
class KotlinUCollectionLiteralExpression(
    override val sourcePsi: KtCollectionLiteralExpression,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UCallExpressionEx, DelegatedMultiResolve, KotlinUElementWithType {

    private val methodIdentifierPart = UastLazyPart<UIdentifier?>()
    private val valueArgumentsPart = UastLazyPart<List<UExpression>>()

    override val classReference: UReferenceExpression? get() = null

    override val kind: UastCallKind = UastCallKind.NESTED_ARRAY_INITIALIZER

    override val methodIdentifier: UIdentifier?
        get() = methodIdentifierPart.getOrBuild { UIdentifier(sourcePsi.leftBracket, this) }

    override val methodName: String? get() = null

    override val receiver: UExpression? get() = null

    override val receiverType: PsiType? get() = null

    override val returnType: PsiType? get() = getExpressionType()

    override val typeArgumentCount: Int get() = typeArguments.size

    override val typeArguments: List<PsiType> get() = listOfNotNull((returnType as? PsiArrayType)?.componentType)

    override val valueArgumentCount: Int
        get() = sourcePsi.getInnerExpressions().size

    override val valueArguments: List<UExpression>
        get() = valueArgumentsPart.getOrBuild {
            sourcePsi.getInnerExpressions().map {
                baseResolveProviderService.baseKotlinConverter.convertOrEmpty(it, this)
            }
        }

    override fun asRenderString(): String = "collectionLiteral[" + valueArguments.joinToString { it.asRenderString() } + "]"

    override fun resolve(): PsiMethod? = null

    override val psi: PsiElement get() = sourcePsi

    override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

}
