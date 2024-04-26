// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java.expressions

import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.java.*
import org.jetbrains.uast.java.JavaConverter

@ApiStatus.Internal
class JavaUUnamedPatternExpression(
  override val sourcePsi: PsiUnnamedPattern,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UPatternExpression {
  override val typeReference: UTypeReferenceExpression? = null

  override val variable: UParameter? = null

  override val deconstructedPatterns: List<UPatternExpression> = emptyList()
}

@ApiStatus.Internal
class JavaUTypePatternExpression(
  override val sourcePsi: PsiTypeTestPattern,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UPatternExpression {
  private val variablePart = UastLazyPart<UParameter?>()

  override val variable: UParameter? = variablePart.getOrBuild {
    sourcePsi.patternVariable?.let { patternVariable ->  JavaUParameter(patternVariable, this) }
  }

  override val deconstructedPatterns: List<UPatternExpression> = emptyList()
}

@ApiStatus.Internal
class JavaUDeconstructionPatternPattern(
  override val sourcePsi: PsiDeconstructionPattern,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UPatternExpression {
  private val typeReferencePart = UastLazyPart<UTypeReferenceExpression>()

  private val patternsPart = UastLazyPart<List<UPatternExpression>>()

  override val typeReference: UTypeReferenceExpression = typeReferencePart.getOrBuild {
    JavaUTypeReferenceExpression(sourcePsi.typeElement, this)
  }

  override val variable: UParameter? = null

  override val deconstructedPatterns: List<UPatternExpression>
    get() = patternsPart.getOrBuild {
      sourcePsi.deconstructionList.deconstructionComponents.mapNotNull { component ->
        JavaConverter.convertPsiElement(component, this, UPatternExpression::class.java) as? UPatternExpression
      }
    }
}