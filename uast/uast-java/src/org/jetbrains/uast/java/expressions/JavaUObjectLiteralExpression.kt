// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUObjectLiteralExpression(
  override val sourcePsi: PsiNewExpression,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), UObjectLiteralExpression, UCallExpression, UMultiResolvable {
  private val declarationPart = UastLazyPart<UClass>()
  private val classReferencePart = UastLazyPart<UReferenceExpression?>()
  private val valueArgumentsPart = UastLazyPart<List<UExpression>>()
  private var typeArgumentCountLazy = Int.MIN_VALUE

  override val declaration: UClass
    get() = declarationPart.getOrBuild { JavaUClass.create(sourcePsi.anonymousClass!!, this) }

  override val classReference: UReferenceExpression?
    get() = classReferencePart.getOrBuild {
      sourcePsi.classReference?.let { ref ->
        JavaConverter.convertReference(ref, this, UElement::class.java) as? UReferenceExpression
      }
    }

  override val valueArgumentCount: Int
    get() = sourcePsi.argumentList?.expressions?.size ?: 0

  override val valueArguments: List<UExpression>
    get() = valueArgumentsPart.getOrBuild {
      sourcePsi.argumentList?.expressions?.map { JavaConverter.convertOrEmpty(it, this) } ?: emptyList()
    }

  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override val typeArgumentCount: Int
    get() {
      if (typeArgumentCountLazy == Int.MIN_VALUE) {
        typeArgumentCountLazy = sourcePsi.classReference?.typeParameters?.size ?: 0
      }
      return typeArgumentCountLazy
    }

  override val typeArguments: List<PsiType>
    get() = sourcePsi.classReference?.typeParameters?.toList() ?: emptyList()

  override fun resolve(): PsiMethod? = sourcePsi.resolveMethod()

  override fun multiResolve(): Iterable<ResolveResult> =
    sourcePsi.classReference?.multiResolve(false)?.asIterable() ?: emptyList()
}
