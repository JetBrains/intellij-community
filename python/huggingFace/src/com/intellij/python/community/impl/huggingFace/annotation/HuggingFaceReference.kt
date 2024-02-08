// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.annotation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase

class HuggingFaceModelReference(
  element: PsiElement,
  textRange: TextRange,
  private val modelName: String
) : PsiReferenceBase<PsiElement>(element, textRange) {
  override fun resolve(): PsiElement {
    return HuggingFaceModelPsiElement(myElement, modelName)
  }
}

class HuggingFaceDatasetReference(
  element: PsiElement,
  textRange: TextRange,
  private val datasetName: String
) : PsiReferenceBase<PsiElement>(element, textRange) {
  override fun resolve(): PsiElement {
    return HuggingFaceDatasetPsiElement(myElement, datasetName)
  }
}
