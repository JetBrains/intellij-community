package com.intellij.debugger.streams.psi.impl

import com.intellij.debugger.streams.psi.PsiElementTransformer
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor

/**
 * @author Vitaliy.Bibaev
 */
object ToObjectInheritorTransformer: PsiElementTransformer.Base() {
  override val visitor: PsiElementVisitor
    get() = object : JavaElementVisitor() {}
}