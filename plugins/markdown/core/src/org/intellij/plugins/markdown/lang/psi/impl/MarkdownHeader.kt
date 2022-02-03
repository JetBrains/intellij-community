// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.elementType
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.stubs.impl.MarkdownHeaderStubElement
import org.intellij.plugins.markdown.lang.stubs.impl.MarkdownHeaderStubElementType
import org.intellij.plugins.markdown.structureView.MarkdownStructureColors
import javax.swing.Icon

@Suppress("DEPRECATION")
class MarkdownHeader: MarkdownHeaderImpl {
  constructor(node: ASTNode): super(node)
  constructor(stub: MarkdownHeaderStubElement, type: MarkdownHeaderStubElementType): super(stub, type)

  val level
    get() = calculateHeaderLevel()

  override fun accept(visitor: PsiElementVisitor) {
    @Suppress("DEPRECATION")
    when (visitor) {
      is MarkdownElementVisitor -> visitor.visitHeader(this)
      else -> super.accept(visitor)
    }
  }

  override fun getPresentation(): ItemPresentation {
    val headerText = getHeaderText()
    val text = headerText ?: "Invalid header: $text"
    return object: ColoredItemPresentation {
      override fun getPresentableText(): String {
        val prevSibling = prevSibling
        if (Registry.`is`("markdown.structure.view.list.visibility") && MarkdownTokenTypeSets.LIST_MARKERS.contains(prevSibling.elementType)) {
          return prevSibling.text + text
        }
        return text
      }

      override fun getIcon(open: Boolean): Icon? = null

      override fun getTextAttributesKey(): TextAttributesKey? {
        return when (level) {
          1 -> MarkdownStructureColors.MARKDOWN_HEADER_BOLD
          else -> MarkdownStructureColors.MARKDOWN_HEADER
        }
      }
    }
  }

  override fun getName(): String? {
    return getHeaderText()
  }

  private fun getHeaderText(): String? {
    if (!isValid) {
      return null
    }
    val contentHolder = findChildByType<PsiElement>(MarkdownTokenTypeSets.INLINE_HOLDING_ELEMENT_TYPES) ?: return null
    return StringUtil.trim(contentHolder.text)
  }
}
