// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java.expressions

import com.intellij.psi.PsiDeconstructionPattern
import com.intellij.psi.PsiTypeTestPattern
import com.intellij.psi.PsiUnnamedPattern
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.java.JavaAbstractUExpression
import org.jetbrains.uast.java.JavaConverter
import org.jetbrains.uast.java.JavaUTypeReferenceExpression

@ApiStatus.Internal
class JavaUUnamedPatternExpression(
  override val sourcePsi: PsiUnnamedPattern,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UPatternExpression {
  override val name: String? = null

  override val typeReference: UTypeReferenceExpression? = null

  override val deconstructedPatterns: List<UPatternExpression> = emptyList()
}

@ApiStatus.Internal
class JavaUTypePatternExpression(
  override val sourcePsi: PsiTypeTestPattern,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UPatternExpression {
  private val typeReferencePart = UastLazyPart<UTypeReferenceExpression?>()

  override val name: String? = sourcePsi.patternVariable?.name

  override val deconstructedPatterns: List<UPatternExpression> = emptyList()

  override val typeReference: UTypeReferenceExpression? = typeReferencePart.getOrBuild {
    sourcePsi.checkType?.let { typeElem -> JavaUTypeReferenceExpression(typeElem, this) }
  }
}

@ApiStatus.Internal
class JavaUDeconstructionPatternPattern(
  override val sourcePsi: PsiDeconstructionPattern,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UPatternExpression {
  private val typeReferencePart = UastLazyPart<UTypeReferenceExpression>()

  private val patternsPart = UastLazyPart<List<UPatternExpression>>()

  override val name: String? = null

  override val deconstructedPatterns: List<UPatternExpression>
    get() = patternsPart.getOrBuild {
      sourcePsi.deconstructionList.deconstructionComponents.mapNotNull { component ->
        JavaConverter.convertPsiElement(component, this, UPatternExpression::class.java) as? UPatternExpression
      }
    }

  override val typeReference: UTypeReferenceExpression = typeReferencePart.getOrBuild {
    JavaUTypeReferenceExpression(sourcePsi.typeElement, this)
  }
}