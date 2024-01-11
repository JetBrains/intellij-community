package org.intellij.plugins.markdown.lang.references

import com.intellij.openapi.paths.WebReference
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.lang.psi.util.hasType

internal class AutoLinkWebReferenceContributor: PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(autoLinksPattern, WebReferenceContributor())
  }

  private class WebReferenceContributor: PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      if (element.hasType(MarkdownTokenTypeSets.AUTO_LINKS) && element.parent !is MarkdownLinkDestination) {
        val link = when (element.elementType) {
          MarkdownTokenTypes.EMAIL_AUTOLINK -> WebReference(element, "mailto:${element.text}")
          else -> WebReference(element)
        }
        return arrayOf(link)
      }
      return emptyArray()
    }
  }

  companion object {
    private val autoLinksPattern = PlatformPatterns.psiElement().inFile(PlatformPatterns.psiFile(MarkdownFile::class.java))
  }
}
