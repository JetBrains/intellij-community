// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

/**
 * @author Vitaliy.Bibaev
 */
interface PsiElementTransformer {
  fun transform(element: PsiElement)

  abstract class Base: PsiElementTransformer {
    override fun transform(element: PsiElement) {
      element.accept(visitor)
    }

    protected abstract val visitor: PsiElementVisitor
  }
}