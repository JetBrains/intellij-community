// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.references

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MarkdownTextReferenceProvider {
  fun getReferences(element: PsiElement): Array<PsiReference>?

  companion object {
    private val EP = ExtensionPointName<MarkdownTextReferenceProvider>("org.intellij.markdown.textReferenceProvider")

    @JvmStatic
    fun getReferencesFor(element: PsiElement): Array<PsiReference>? {
      val references = EP.extensionList.flatMap { it.getReferences(element)?.asList().orEmpty() }
      return if (references.isEmpty()) null else references.toTypedArray()
    }
  }
}
