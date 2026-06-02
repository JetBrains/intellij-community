// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.references.test

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTestLink
import org.intellij.plugins.markdown.lang.references.ReferenceUtil.isRelativePathLike

/**
 * Provides file references for the path component of `[@test] ../path/to/file` constructs.
 */
internal class TestPsiReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(MarkdownTestLink::class.java),
      TestPsiReferenceProvider()
    )
  }

  private class TestPsiReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      if (element !is MarkdownTestLink) return PsiReference.EMPTY_ARRAY

      val path = element.text
      if (path.isNullOrEmpty()) return PsiReference.EMPTY_ARRAY
      if (!path.isRelativePathLike()) return PsiReference.EMPTY_ARRAY

      val containingFile = element.containingFile?.originalFile ?: return PsiReference.EMPTY_ARRAY
      val parentDirectory = containingFile.virtualFile?.parent ?: return PsiReference.EMPTY_ARRAY
      val contextDirectory = containingFile.manager.findDirectory(parentDirectory) ?: return PsiReference.EMPTY_ARRAY

      val references = object : FileReferenceSet(path, element, 0, null, true, false) {
        override fun isSoft(): Boolean = true
        override fun computeDefaultContexts(): Collection<PsiFileSystemItem> = listOf(contextDirectory)
      }.allReferences
      return Array(references.size) { references[it] }
    }
  }
}
