// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UastBinaryExpressionWithTypeKind
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild

@ApiStatus.Internal
class KotlinCustomUBinaryExpressionWithType(
    override val psi: PsiElement,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UBinaryExpressionWithType {

    private val typePart = UastLazyPart<PsiType>()

    override lateinit var operand: UExpression
        internal set

    override lateinit var operationKind: UastBinaryExpressionWithTypeKind
        internal set

    override val type: PsiType
        get() = typePart.getOrBuild { typeReference?.type ?: UastErrorType }

    override var typeReference: UTypeReferenceExpression? = null
        internal set
}
