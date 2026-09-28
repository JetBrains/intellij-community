package com.intellij.markdown.backend.reference.link

import com.intellij.markdown.backend.reference.GithubWikiLocalFileReferenceProvider
import com.intellij.openapi.paths.PathReference
import com.intellij.openapi.paths.PathReferenceManager
import com.intellij.openapi.paths.PathReferenceProviderBase
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownImage
import org.intellij.plugins.markdown.lang.references.ReferenceUtil

internal class CommonLinkDestinationReferenceContributor: PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(ReferenceUtil.linkDestinationPattern, CommonLinkDestinationReferenceProvider())
  }

  private class CommonLinkDestinationReferenceProvider: PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
      if (GithubWikiLocalFileReferenceProvider.isDomainlessLink(element.text)) return emptyArray()
      val manager = PathReferenceManager.getInstance()
      if ((element.parent?.parent as? MarkdownImage)?.linkDestination != element) {
        return manager.createReferences(element, false, false, true)
      }
      return manager.createCustomReferences(element, false, manager.globalWebPathReferenceProvider, ImagePathReferenceProvider)
    }
  }

  /** Resolves an image destination from the directory of the document, and then from the project root */
  private object ImagePathReferenceProvider: PathReferenceProviderBase() {
    override fun createReferences(
      psiElement: PsiElement, offset: Int, text: String, references: MutableList<in PsiReference>, soft: Boolean,
    ): Boolean {
      val referenceSet = object: FileReferenceSet(text, psiElement, offset, null, true, false, null) {
        override fun isUrlEncoded(): Boolean = true
        override fun isSoft(): Boolean = soft
        override fun computeDefaultContexts(): Collection<PsiFileSystemItem> {
          val contexts = super.computeDefaultContexts()
          val file = containingFile ?: return contexts
          val document = file.virtualFile ?: return contexts
          val projectRoot = BaseProjectDirectories.getInstance(file.project).getBaseDirectoryFor(document) ?: return contexts
          val directory = file.manager.findDirectory(projectRoot) ?: return contexts
          return (contexts + directory).distinct()
        }

        override fun createFileReference(range: TextRange, index: Int, text: String): FileReference {
          return ImageFileReference(this, range, index, text)
        }
      }
      references.addAll(referenceSet.allReferences)
      return true
    }

    override fun getPathReference(path: String, element: PsiElement): PathReference? = null
  }

  private class ImageFileReference(referenceSet: FileReferenceSet, range: TextRange, index: Int, text: String) :
    FileReference(referenceSet, range, index, text) {
    override fun resolve(): PsiFileSystemItem? {
      return multiResolve(false).firstOrNull { it.isValidResult }?.element as? PsiFileSystemItem
    }
  }
}