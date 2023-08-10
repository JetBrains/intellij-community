package org.intellij.plugins.markdown.lang.references

import com.intellij.openapi.paths.PathReferenceManager
import com.intellij.psi.*
import com.intellij.util.ProcessingContext

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
