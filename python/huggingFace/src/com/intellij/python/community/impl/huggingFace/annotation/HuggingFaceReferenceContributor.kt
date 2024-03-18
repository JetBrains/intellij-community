// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.annotation

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.jetbrains.python.PyElementTypes


class HuggingFaceModelReferenceContributor: PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(PyElementTypes.STRING_LITERAL_EXPRESSION),
      HuggingFaceModelReferenceProvider(),
      PsiReferenceRegistrar.DEFAULT_PRIORITY
    )
  }
}

class HuggingFaceDatasetReferenceContributor: PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(PyElementTypes.STRING_LITERAL_EXPRESSION),
      HuggingFaceDatasetReferenceProvider(),
      PsiReferenceRegistrar.DEFAULT_PRIORITY
    )
  }
}
