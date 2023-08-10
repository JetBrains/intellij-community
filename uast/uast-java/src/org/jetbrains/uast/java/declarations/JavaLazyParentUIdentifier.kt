// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java.declarations

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import org.jetbrains.uast.*

internal class JavaLazyParentUIdentifier(psi: PsiElement?, givenParent: UElement?)
  : LazyParentUIdentifier(psi, givenParent) {
  override fun computeParent(): UElement? {
    val parent = sourcePsi?.parent ?: return null
    if (parent is PsiReferenceExpression && parent.parent is PsiMethodCallExpression) {
      return parent.parent.toUElementOfType<UCallExpression>() ?: parent.toUElement()
    }
    return parent.toUElement()
  }
}