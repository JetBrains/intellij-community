// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.service

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.python.community.impl.huggingFace.annotation.HuggingFaceIdentifierPsiElement.Companion.HUGGING_FACE_ENTITY_NAME_KEY
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyPlainStringElementImpl


class HuggingFaceTypoInspectionSuppressor : InspectionSuppressor {
  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    if (element is PsiFileSystemItem || element.containingFile !is PyFile || "SpellCheckingInspection" != toolId) {
      return false
    }
    if (element !is PyPlainStringElementImpl) {
      return false
    }
    val parent = element.getParent()
    if (parent == null) {
      return false
    }
    val ignoreSpellCheck = parent.getUserData(HUGGING_FACE_ENTITY_NAME_KEY)
    return ignoreSpellCheck == true
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
    return SuppressQuickFix.EMPTY_ARRAY
  }
}
