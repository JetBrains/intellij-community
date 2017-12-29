// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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