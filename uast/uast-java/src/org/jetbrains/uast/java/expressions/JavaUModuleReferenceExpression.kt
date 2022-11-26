// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java.expressions

import com.intellij.psi.*
import com.intellij.util.asSafely
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMultiResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.java.JavaAbstractUExpression

internal class JavaUModuleReferenceExpression(
  override val sourcePsi: PsiJavaModuleReferenceElement,
  givenParent: UElement?
) : JavaAbstractUExpression(givenParent), USimpleNameReferenceExpression, UMultiResolvable {

  override val identifier: String
    get() = sourcePsi.referenceText

  override val resolvedName: String?
    get() = resolve().asSafely<PsiNamedElement>()?.name

  override fun resolve(): PsiElement? {
    return sourcePsi.reference?.resolve()
  }

  override fun multiResolve(): Iterable<ResolveResult> = resolve()?.let { listOf(PsiElementResolveResult(it)) } ?: emptyList()
}