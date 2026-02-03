package com.intellij.markdown.backend.lang.references

import com.intellij.openapi.paths.PathReferenceManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.lang.references.ReferenceUtil

internal class CommonLinkDestinationReferenceContributor: PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(ReferenceUtil.linkDestinationPattern, CommonLinkDestinationReferenceProvider())
  }

  private class CommonLinkDestinationReferenceProvider: PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      return PathReferenceManager.getInstance().createReferences(element, false, true, true)
    }
  }
}