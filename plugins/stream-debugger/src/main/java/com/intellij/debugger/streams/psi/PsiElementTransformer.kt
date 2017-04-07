package com.intellij.debugger.streams.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

/**
 * @author Vitaliy.Bibaev
 */
interface PsiElementTransformer {
  fun transform(element: PsiElement): Unit

  abstract class Base: PsiElementTransformer {
    override fun transform(element: PsiElement) {
      element.accept(visitor)
    }

    protected abstract val visitor: PsiElementVisitor
  }
}