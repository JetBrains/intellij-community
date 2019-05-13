// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.references

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

/**
 * EP to check if some reference points to some element
 */
interface PyReferenceCustomTargetChecker {
  companion object {
    val EP_NAME = ExtensionPointName.create<PyReferenceCustomTargetChecker>("Pythonid.pyReferenceCustomTargetChecker")
    fun isReferenceTo(reference: PsiReference, to: PsiElement) = EP_NAME.extensions.firstOrNull { it.isReferenceTo(reference, to) } != null
  }

  fun isReferenceTo(reference: PsiReference, to: PsiElement): Boolean
}