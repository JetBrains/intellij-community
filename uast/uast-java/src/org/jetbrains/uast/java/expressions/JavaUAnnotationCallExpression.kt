/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.uast.java.expressions

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiType
import org.jetbrains.uast.*
import org.jetbrains.uast.java.JavaAbstractUExpression
import org.jetbrains.uast.java.JavaConverter
import org.jetbrains.uast.java.JavaUAnnotation
import org.jetbrains.uast.java.lz
import org.jetbrains.uast.visitor.UastVisitor

class JavaUAnnotationCallExpression(
  override val psi: PsiAnnotation,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UCallExpressionEx {

  val uAnnotation by lz {
    JavaUAnnotation(psi, this)
  }

  override val returnType: PsiType?
    get() = uAnnotation.qualifiedName?.let { PsiType.getTypeByName(it, psi.project, psi.resolveScope) }

  override val kind: UastCallKind
    get() = UastCallKind.CONSTRUCTOR_CALL

  override val methodName: String?
    get() = null

  override val receiver: UExpression?
    get() = null
  override val receiverType: PsiType?
    get() = null

  override val methodIdentifier: UIdentifier?
    get() = null

  override val classReference by lz {
    psi.nameReferenceElement?.let { ref ->
      JavaConverter.convertReference(ref, this, null) as? UReferenceExpression
    }
  }

  override val valueArgumentCount: Int
    get() = psi.parameterList.attributes.size

  override val valueArguments by lz {
    uAnnotation.attributeValues
  }

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override fun accept(visitor: UastVisitor) {
    visitor.visitCallExpression(this)
    uAnnotation.accept(visitor)
    visitor.afterVisitCallExpression(this)
  }

  override val typeArgumentCount = 0

  override val typeArguments: List<PsiType> = emptyList()

  override fun resolve() = uAnnotation.resolve()?.constructors?.firstOrNull()
}
