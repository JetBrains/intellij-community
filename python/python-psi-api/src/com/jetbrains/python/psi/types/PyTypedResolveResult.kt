// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult

class PyTypedResolveResult(private val el: PsiElement?, val type: PyType?) : ResolveResult {
  override fun getElement(): PsiElement? {
    return el
  }
  override fun isValidResult(): Boolean {
    return element != null
  }
}