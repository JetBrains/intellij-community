// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMock

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.jetbrains.python.psi.PyStringLiteralExpression

/**
 * Registers reference providers for `unittest.mock.patch` and `unittest.mock.patch.object`
 * string targets. Enables navigation, completion, and error highlighting.
 */
internal class PyMockReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    val pattern = PlatformPatterns.psiElement(PyStringLiteralExpression::class.java)
    registrar.registerReferenceProvider(pattern, PyMockPatchReferenceProvider())
    registrar.registerReferenceProvider(pattern, PyMockPatchObjectReferenceProvider())
  }
}
