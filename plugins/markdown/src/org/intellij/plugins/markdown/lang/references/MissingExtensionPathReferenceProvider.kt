// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.references

import com.intellij.openapi.paths.PathReference
import com.intellij.openapi.paths.PathReferenceProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

class MissingExtensionPathReferenceProvider : PathReferenceProvider {
  override fun createReferences(psiElement: PsiElement, references: MutableList<PsiReference>, soft: Boolean): Boolean {
    RelativeMissingExtensionFileReference.createReference(psiElement, references, soft)
    return false
  }

  override fun getPathReference(path: String, element: PsiElement): PathReference? = null
}