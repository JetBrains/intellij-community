package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement
import org.intellij.plugins.markdown.lang.psi.util.childrenOfType
import org.intellij.plugins.markdown.structureView.MarkdownBasePresentation
import org.jetbrains.annotations.ApiStatus

class MarkdownLinkDefinition(node: ASTNode): ASTWrapperPsiElement(node), MarkdownPsiElement {
  val linkLabel: MarkdownLinkLabel
    get() = findChildByType(MarkdownElementTypes.LINK_LABEL) ?: error("Failed to find link label. Seems link parsing has failed.")

  val linkDestination: MarkdownLinkDestination
    get() = findChildByType(MarkdownElementTypes.LINK_DESTINATION) ?: error("Failed to find link destination. Seems link parsing has failed.")

  val linkTitle: PsiElement?
    get() = findChildByType(MarkdownElementTypes.LINK_TITLE)

  internal val isCommentWrapper
    get() = childrenOfType(MarkdownElementTypes.LINK_COMMENT).any()

  override fun getPresentation(): ItemPresentation {
    return LinkDefinitionPresentation()
  }

  private inner class LinkDefinitionPresentation: MarkdownBasePresentation() {
    override fun getPresentableText(): String? {
      return when {
        !isValid -> null
        else -> "Def: ${linkLabel.text} → ${linkDestination.text}"
      }
    }

    override fun getLocationString(): String? {
      return when {
        !isValid -> null
        else -> linkTitle?.text
      }
    }
  }

  companion object {
    @ApiStatus.Internal
    fun isUnderCommentWrapper(element: PsiElement): Boolean {
      val linkDefinition = element.parentOfType<MarkdownLinkDefinition>(withSelf = true)
      return linkDefinition?.isCommentWrapper == true
    }
  }
}
