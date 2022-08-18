// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement

@ApiStatus.Internal
class KotlinUDestructuringDeclarationExpression(
    givenParent: UElement?,
    psiAnchor: PsiElement,
) : KotlinUDeclarationsExpression(null, givenParent, psiAnchor) {

    val tempVarAssignment get() = declarations.first()
}
