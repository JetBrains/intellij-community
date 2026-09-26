// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyMonkeypatch

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.jetbrains.python.psi.PyStringLiteralExpression

/**
 * Registers reference providers for `monkeypatch.setattr` and `monkeypatch.delattr`
 * string arguments. Enables navigation, completion, and error highlighting.
 */
internal class PyMonkeypatchReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(PyStringLiteralExpression::class.java),
      PyMonkeypatchSetAttrReferenceProvider(),
    )
  }
}
