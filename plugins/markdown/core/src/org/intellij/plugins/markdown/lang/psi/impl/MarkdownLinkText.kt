package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.ResolveResult
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement
import org.intellij.plugins.markdown.lang.psi.util.children
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class MarkdownLinkText(node: ASTNode): ASTWrapperPsiElement(node), MarkdownPsiElement, PsiExternalReferenceHost {
  /**
   * Actual content elements without opening bracket at the start and closing backet at the end.
   */
  val contentElements: Sequence<PsiElement> get() {
    val openBracket = firstChild?.takeIf { it.hasType(MarkdownTokenTypes.LBRACKET) }
    val closeBracket = lastChild?.takeIf { it.hasType(MarkdownTokenTypes.RBRACKET) }
    return children().filterNot { it == openBracket || it == closeBracket }
  }

  override fun getReferences(): Array<PsiReference>  {
    val destination = parent.children.filterIsInstance<MarkdownLinkDestination>().firstOrNull() ?: return PsiReference.EMPTY_ARRAY
    val valueRange = ElementManipulators.getValueTextRange(destination)
    if (valueRange.isEmpty) return PsiReference.EMPTY_ARRAY
    val destinationReference = destination.findReferenceAt(valueRange.endOffset - 1) ?: return PsiReference.EMPTY_ARRAY
    return arrayOf(LinkTextReference(this, destinationReference))
  }

  private class LinkTextReference(element: MarkdownLinkText, private val delegate: PsiReference) :
    PsiReferenceBase.Poly<MarkdownLinkText>(element, TextRange.allOf(element.text), delegate.isSoft) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
      if (delegate is PsiPolyVariantReference) {
        return delegate.multiResolve(incompleteCode)
      }
      val resolved = delegate.resolve() ?: return ResolveResult.EMPTY_ARRAY
      return arrayOf(PsiElementResolveResult(resolved))
    }
  }
}
