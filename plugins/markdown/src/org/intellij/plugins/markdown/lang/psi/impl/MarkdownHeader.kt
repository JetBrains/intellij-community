// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.elementType
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor
import org.intellij.plugins.markdown.lang.stubs.MarkdownStubBasedPsiElementBase
import org.intellij.plugins.markdown.lang.stubs.MarkdownStubElement
import org.intellij.plugins.markdown.lang.stubs.impl.MarkdownHeaderStubElement
import org.intellij.plugins.markdown.lang.stubs.impl.MarkdownHeaderStubElementType
import org.intellij.plugins.markdown.structureView.MarkdownStructureColors
import javax.swing.Icon

open class MarkdownHeader: MarkdownStubBasedPsiElementBase<MarkdownStubElement<*>>, PsiExternalReferenceHost {
  constructor(node: ASTNode): super(node)
  constructor(stub: MarkdownHeaderStubElement, type: MarkdownHeaderStubElementType): super(stub, type)

  val level
    get() = calculateHeaderLevel()

  override fun accept(visitor: PsiElementVisitor) {
    if (visitor is MarkdownElementVisitor) {
      visitor.visitHeader(this)
      return
    }
    super.accept(visitor)
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

  private fun calculateHeaderLevel(): Int {
    val type = node.elementType
    return when {
      MarkdownTokenTypeSets.HEADER_LEVEL_1_SET.contains(type) -> 1
      MarkdownTokenTypeSets.HEADER_LEVEL_2_SET.contains(type) -> 2
      type == MarkdownElementTypes.ATX_3 -> 3
      type == MarkdownElementTypes.ATX_4 -> 4
      type == MarkdownElementTypes.ATX_5 -> 5
      type == MarkdownElementTypes.ATX_6 -> 6
      else -> throw IllegalStateException("Type should be one of header types")
    }
  }
}
