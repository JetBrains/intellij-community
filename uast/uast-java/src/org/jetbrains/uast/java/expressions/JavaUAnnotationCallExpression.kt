/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.uast.java.expressions

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.java.JavaAbstractUExpression
import org.jetbrains.uast.java.JavaConverter
import org.jetbrains.uast.java.JavaUAnnotation
import org.jetbrains.uast.java.lz
import org.jetbrains.uast.visitor.UastVisitor

@ApiStatus.Internal
class JavaUAnnotationCallExpression(
  override val sourcePsi: PsiAnnotation,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UCallExpression, UMultiResolvable {

  // TODO: once there is no more external usage, this property can be `private`.
  @get:ApiStatus.Internal
  val uAnnotation: JavaUAnnotation by lz {
    JavaUAnnotation(sourcePsi, this)
  }

  override val returnType: PsiType?
    get() = uAnnotation.qualifiedName?.let { PsiType.getTypeByName(it, sourcePsi.project, sourcePsi.resolveScope) }

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

  override val classReference: UReferenceExpression? by lz {
    sourcePsi.nameReferenceElement?.let { ref ->
      JavaConverter.convertReference(ref, this) as? UReferenceExpression
    }
  }

  override val valueArgumentCount: Int
    get() = sourcePsi.parameterList.attributes.size

  override val valueArguments: List<UNamedExpression> by lz {
    uAnnotation.attributeValues
  }

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override fun accept(visitor: UastVisitor) {
    visitor.visitCallExpression(this)
    uAnnotation.accept(visitor)
    visitor.afterVisitCallExpression(this)
  }

  override val typeArgumentCount: Int = 0

  override val typeArguments: List<PsiType> = emptyList()

  override fun resolve(): PsiMethod? = uAnnotation.resolve()?.constructors?.firstOrNull()

  override fun multiResolve(): Iterable<ResolveResult> = uAnnotation.multiResolve()
}
