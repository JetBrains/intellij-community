// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.model.psi.labels

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDefinition
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkLabel
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.util.isFootnoteLabelText

internal class LinkDefinitions private constructor(val definitions: List<MarkdownLinkDefinition>) {
  private val firstByLabel = LinkedHashMap<String, MarkdownLinkDefinition>().apply {
    for (definition in definitions) {
      putIfAbsent(normalizeLinkLabel(definition.linkLabel.labelText), definition)
    }
  }

  fun find(label: String): MarkdownLinkDefinition? = firstByLabel[normalizeLinkLabel(label)]

  companion object {
    private val leafBlockTypes = TokenSet.orSet(
      MarkdownTokenTypeSets.HEADERS,
      TokenSet.create(
        MarkdownElementTypes.PARAGRAPH,
        MarkdownElementTypes.CODE_FENCE,
        MarkdownElementTypes.CODE_BLOCK,
        MarkdownElementTypes.HTML_BLOCK,
        MarkdownElementTypes.TABLE,
        MarkdownElementTypes.LINK_DEFINITION,
      ),
    )

    fun get(file: PsiFile): LinkDefinitions = CachedValuesManager.getCachedValue(file) {
      val definitions = ArrayList<MarkdownLinkDefinition>()
      for (element in SyntaxTraverser.psiTraverser(file).expand { !it.hasType(leafBlockTypes) }) {
        ProgressManager.checkCanceled()
        if (element is MarkdownLinkDefinition && element.isUsable()) {
          definitions.add(element)
        }
      }
      CachedValueProvider.Result.create(LinkDefinitions(definitions), file)
    }

    private fun MarkdownLinkDefinition.isUsable(): Boolean {
      val label = PsiTreeUtil.getChildOfType(this, MarkdownLinkLabel::class.java) ?: return false
      if (label.labelText.isBlank() || isFootnoteLabelText(label.text) || MarkdownLinkDefinition.isUnderCommentWrapper(this)) {
        return false
      }
      return PsiTreeUtil.getChildOfType(this, MarkdownLinkDestination::class.java) != null
    }
  }
}
