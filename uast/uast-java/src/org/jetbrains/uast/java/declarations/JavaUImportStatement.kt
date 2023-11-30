// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.java

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.ResolveResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUImportStatement(
  override val sourcePsi: PsiImportStatementBase,
  uastParent: UElement?
) : JavaAbstractUElement(uastParent), UImportStatement, UMultiResolvable {

  private val importReferencePart = UastLazyPart<UElement?>()

  override val isOnDemand: Boolean
    get() = sourcePsi.isOnDemand
  override val importReference: UElement?
    get() = importReferencePart.getOrBuild { sourcePsi.importReference?.let { JavaDumbUElement(it, this, it.qualifiedName) } }

  override fun resolve(): PsiElement? = sourcePsi.resolve()
  override fun multiResolve(): Iterable<ResolveResult> =
    sourcePsi.importReference?.multiResolve(false)?.asIterable() ?: emptyList()

  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiImportStatementBase get() = sourcePsi
}
