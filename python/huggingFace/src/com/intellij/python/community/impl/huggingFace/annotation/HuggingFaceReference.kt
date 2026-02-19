// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.annotation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
class HuggingFaceReference(
  element: PsiElement,
  textRange: TextRange,
  private val identifier: String,
  private val entityKind: HuggingFaceEntityKind,
) : PsiReferenceBase<PsiElement>(element, textRange) {
  override fun resolve(): PsiElement {
    return HuggingFaceIdentifierPsiElement(myElement, identifier, entityKind)
  }
}
