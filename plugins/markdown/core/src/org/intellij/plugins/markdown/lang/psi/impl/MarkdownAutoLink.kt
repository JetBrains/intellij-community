package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.tree.IElementType

class MarkdownAutoLink(
  type: IElementType,
  text: CharSequence
): MarkdownLeafPsiElement(type, text), PsiExternalReferenceHost {
  override fun getReferences(): Array<PsiReference?> {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this)
  }

  internal class Manipulator: LeafElementManipulator<MarkdownAutoLink>()
}
