// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.uast.*

class KotlinCustomUBinaryExpressionWithType(
    override val psi: PsiElement,
    givenParent: UElement?
) : KotlinAbstractUExpression(givenParent), UBinaryExpressionWithType {
    override lateinit var operand: UExpression
        internal set

    override lateinit var operationKind: UastBinaryExpressionWithTypeKind
        internal set

    override val type: PsiType by lz { typeReference?.type ?: UastErrorType }

    override var typeReference: UTypeReferenceExpression? = null
        internal set
}
