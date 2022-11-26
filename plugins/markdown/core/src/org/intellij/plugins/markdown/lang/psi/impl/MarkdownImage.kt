// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement
import org.intellij.plugins.markdown.lang.psi.util.children
import org.intellij.plugins.markdown.lang.psi.util.hasType

class MarkdownImage(node: ASTNode): ASTWrapperPsiElement(node), MarkdownPsiElement, MarkdownLink {
  val exclamationMark: PsiElement?
    get() = firstChild

  val link: MarkdownInlineLink?
    get() = findChildByType(MarkdownElementTypes.INLINE_LINK)

  override val linkText: MarkdownLinkText?
    get() = link?.children()?.filterIsInstance<MarkdownLinkText>()?.firstOrNull()

  override val linkDestination: MarkdownLinkDestination?
    get() = link?.children()?.filterIsInstance<MarkdownLinkDestination>()?.firstOrNull()

  private val linkTitle: PsiElement?
    get() = link?.children()?.find { it.hasType(MarkdownElementTypes.LINK_TITLE) }

  fun collectLinkDescriptionText(): String? {
    return linkText?.let {
      collectTextFromWrappedBlock(it, MarkdownTokenTypes.LBRACKET, MarkdownTokenTypes.RBRACKET)
    }
  }

  fun collectLinkTitleText(): String? {
    return linkTitle?.let {
      collectTextFromWrappedBlock(it, MarkdownTokenTypes.DOUBLE_QUOTE, MarkdownTokenTypes.DOUBLE_QUOTE)
    }
  }

  private fun collectTextFromWrappedBlock(element: PsiElement, prefix: IElementType? = null, suffix: IElementType? = null): String? {
    val children = element.firstChild?.siblings(withSelf = true)?.toList() ?: return null
    val left = when (children.first().elementType) {
      prefix -> 1
      else -> 0
    }
    val right = when (children.last().elementType) {
      suffix -> children.size - 1
      else -> children.size
    }
    val elements = children.subList(left, right)
    val content = elements.joinToString(separator = "") { it.text }
    return content.takeIf { it.isNotEmpty() }
  }

  companion object {
    /**
     * Useful for determining if some element is an actual image by it's leaf child.
     */
    fun getByLeadingExclamationMark(exclamationMark: PsiElement): MarkdownImage? {
      if (!exclamationMark.hasType(MarkdownTokenTypes.EXCLAMATION_MARK)) {
        return null
      }
      val image = exclamationMark.parent ?: return null
      if (!image.hasType(MarkdownElementTypes.IMAGE) || image.firstChild != exclamationMark) {
        return null
      }
      return image as? MarkdownImage
    }
  }
}
