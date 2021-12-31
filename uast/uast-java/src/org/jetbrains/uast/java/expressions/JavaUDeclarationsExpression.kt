// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

@ApiStatus.Internal
class JavaUDeclarationsExpression(
  uastParent: UElement?
) : JavaAbstractUElement(uastParent), UDeclarationsExpression, UElement {
  override val sourcePsi: PsiElement?
    get() = null
  override lateinit var declarations: List<UDeclaration>
    internal set

  constructor(parent: UElement?, declarations: List<UDeclaration>) : this(parent) {
    this.declarations = declarations
  }

  override val uAnnotations: List<UAnnotation>
    get() = emptyList()

  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiElement?
    get() = null

  override fun equals(other: Any?): Boolean = other is JavaUDeclarationsExpression && declarations == other.declarations

  override fun hashCode(): Int = declarations.hashCode()
}
