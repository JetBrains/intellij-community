// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java.expressions

import com.intellij.psi.PsiReferenceParameterList
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.expressions.UTypeArgumentList
import org.jetbrains.uast.java.JavaAbstractUElement
import org.jetbrains.uast.java.JavaConverter
import org.jetbrains.uast.java.lz

class JavaUTypeArgumentList(
  override val sourcePsi: PsiReferenceParameterList,
  givenParent: UElement?,
) : JavaAbstractUElement(givenParent), UTypeArgumentList {
  override val arguments: List<UTypeReferenceExpression> by lz {
    sourcePsi.typeParameterElements.map { JavaConverter.convertPsiElement(it, this) as UTypeReferenceExpression }
  }
}