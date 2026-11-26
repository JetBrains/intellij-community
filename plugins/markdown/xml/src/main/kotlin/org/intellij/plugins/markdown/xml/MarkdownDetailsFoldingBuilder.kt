// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.xml

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.xml.XmlFoldingBuilder
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.util.XmlTagUtil
import org.intellij.markdown.html.entities.Entities
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.settings.MarkdownCodeFoldingSettings

internal class MarkdownDetailsFoldingBuilder : FoldingBuilderEx(), DumbAware {
  private val htmlFoldingBuilder = object : XmlFoldingBuilder() {
    fun buildUnclosedTagFold(tag: XmlTag, document: Document): FoldingDescriptor? {
      val descriptors = mutableListOf<FoldingDescriptor>()
      addToFold(descriptors, tag, document)
      val descriptor = descriptors.singleOrNull() ?: return null
      return FoldingDescriptor(tag.node, descriptor.range, null, descriptor.placeholderText, isCollapsedByDefault(tag.node), emptySet())
        .apply { setCanBeRemovedWhenCollapsed(true) }
    }
  }

  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
    val file = root as? MarkdownFile ?: return FoldingDescriptor.EMPTY_ARRAY
    if (file.language !== file.viewProvider.baseLanguage || !file.viewProvider.contents.contains("<details", ignoreCase = true)) {
      return FoldingDescriptor.EMPTY_ARRAY
    }
    val html = file.viewProvider.getPsi(HTMLLanguage.INSTANCE) ?: return FoldingDescriptor.EMPTY_ARRAY
    val collapse = MarkdownCodeFoldingSettings.getInstance().state.collapseDetails
    return SyntaxTraverser.psiTraverser(html).filterIsInstance<XmlTag>().mapNotNull { tag ->
      if (!tag.name.equals("details", ignoreCase = true)) return@mapNotNull null
      val opening = XmlTagUtil.getStartTagRange(tag) ?: return@mapNotNull null
      val closing = XmlTagUtil.getEndTagRange(tag) ?: return@mapNotNull htmlFoldingBuilder.buildUnclosedTagFold(tag, document)
      if (opening.endOffset > closing.startOffset) return@mapNotNull null
      val text = tag.subTags.firstOrNull { it.name.equals("summary", ignoreCase = true) }?.summaryText() ?: ""
      val summary = StringUtil.collapseWhiteSpace(decodeDetailsSummary(text).replace('\u00a0', ' '))
        .ifEmpty { MarkdownBundle.message("markdown.folding.details.default.summary") }
      val range = htmlFoldingBuilder.getRangeToFold(tag) ?: return@mapNotNull null
      FoldingDescriptor(tag.node, range, null, "▶ $summary", collapse, emptySet())
        .apply { setCanBeRemovedWhenCollapsed(true) }
    }.toTypedArray()
  }

  override fun getPlaceholderText(node: ASTNode): String {
    return "▶ ${MarkdownBundle.message("markdown.folding.details.default.summary")}"
  }

  override fun isCollapsedByDefault(node: ASTNode): Boolean {
    return MarkdownCodeFoldingSettings.getInstance().state.collapseDetails
  }
}

private val detailsEntityRegex = Regex("""&(?:([a-zA-Z0-9]+)|#([0-9]+)|#[xX]([a-fA-F0-9]+));""")

private fun decodeDetailsSummary(text: CharSequence): String {
  return detailsEntityRegex.replace(text) { match ->
    val code = when {
      match.groupValues[1].isNotEmpty() -> Entities.map[match.value] ?: return@replace match.value
      match.groupValues[2].isNotEmpty() -> match.groupValues[2].toIntOrNull()
      else -> match.groupValues[3].toIntOrNull(16)
    }
    if (code != null && code in 1..0x10FFFF && code !in 0xD800..0xDFFF) String(Character.toChars(code)) else "\uFFFD"
  }
}

private fun XmlTag.summaryText(): CharSequence {
  val start = XmlTagUtil.getStartTagRange(this)?.endOffset ?: return ""
  val end = XmlTagUtil.getEndTagRange(this)?.startOffset ?: textRange.endOffset
  return containingFile.viewProvider.contents.subSequence(start, end)
}
