// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.uast.KotlinUDeclarationsExpression
import org.jetbrains.uast.UElement

class KotlinUDestructuringDeclarationExpression(
    givenParent: UElement?,
    psiAnchor: PsiElement,
    baseResolveProviderService: BaseKotlinUastResolveProviderService,
) : KotlinUDeclarationsExpression(null, givenParent, baseResolveProviderService, psiAnchor) {

    val tempVarAssignment get() = declarations.first()
}
