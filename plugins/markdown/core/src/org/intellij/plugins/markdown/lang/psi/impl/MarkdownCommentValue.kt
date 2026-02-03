package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.model.psi.UrlReferenceHost
import com.intellij.psi.PsiLiteralValue
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class MarkdownCommentValue(
  text: CharSequence
): MarkdownLeafPsiElement(MarkdownElementTypes.COMMENT_VALUE, text), UrlReferenceHost, PsiLiteralValue {
  override fun getValue(): Any {
    return text
  }

  override fun getReferences(): Array<PsiReference> {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this)
  }
}
