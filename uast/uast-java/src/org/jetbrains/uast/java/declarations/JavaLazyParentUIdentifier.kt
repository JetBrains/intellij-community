// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java.declarations

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import org.jetbrains.uast.*

internal class JavaLazyParentUIdentifier(psi: PsiElement?, givenParent: UElement?) : LazyParentUIdentifier(psi, givenParent) {

  override val uastParent: UElement? by lazy {
    givenParent?.let { return@lazy it }

    val parent = sourcePsi?.parent ?: return@lazy null
    if (parent is PsiReferenceExpression && parent.parent is PsiMethodCallExpression) {
      return@lazy parent.parent.toUElementOfType<UCallExpression>() ?: parent.toUElement()
    }
    parent.toUElement()
  }

}