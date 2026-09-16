// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.ui.DeferredIconImpl
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.util.hasType
import javax.swing.Icon

class CodeFenceLanguageListCompletionProvider: CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val insertHandler = MyInsertHandler(getIndentForFence(parameters))
    result.addElement(PrioritizedLookupElement.withPriority(
      LookupElementBuilder.create("")
        .withTailText(" (no language)", true)
        .withInsertHandler(insertHandler),
      Double.MAX_VALUE
    ))
    for (provider in CodeFenceLanguageGuesser.customProviders) {
      val lookups = provider.getCompletionVariantsForInfoString(parameters)
      for (lookupElement in lookups) {
        val element = LookupElementDecorator.withInsertHandler(lookupElement) { context: InsertionContext, item: LookupElementDecorator<LookupElement> ->
          insertHandler.handleInsert(context, item)
          lookupElement.handleInsert(context)
        }
        result.addElement(element)
      }
    }
  }

  private class MyInsertHandler(private val fenceSplitIndent: String?): InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
      val indent = fenceSplitIndent ?: return
      val insertionOffset = context.tailOffset
      context.document.insertString(insertionOffset, "\n$indent\n$indent")
      context.editor.caretModel.moveToOffset(insertionOffset + 1 + indent.length)
    }
  }

  companion object {
    @JvmStatic
    fun createLanguageIcon(language: Language): Icon {
      return DeferredIconImpl(null, language, true) { curLanguage: Language -> curLanguage.associatedFileType?.icon }
    }

    private fun getIndentForFence(parameters: CompletionParameters): String? {
      val originalPosition = parameters.originalPosition
      if (!isInMiddleOfUnCollapsedFence(originalPosition, parameters.offset)) {
        return null
      }
      val fenceStart = fenceStartOffset(originalPosition) ?: return ""
      return MarkdownCodeFenceUtils.getIndent(parameters.editor.document, fenceStart)
    }

    @JvmStatic
    fun isInMiddleOfUnCollapsedFence(element: PsiElement?, offset: Int): Boolean {
      return when {
        element == null -> false
        element.hasType(MarkdownTokenTypes.CODE_FENCE_START) -> {
          val range = element.textRange
          range.startOffset + range.endOffset == offset * 2
        }
        element.hasType(MarkdownTokenTypes.TEXT) && element.parent.hasType(MarkdownElementTypes.CODE_SPAN) -> {
          val range = element.textRange
          val parentRange = element.parent.textRange
          range.startOffset - parentRange.startOffset == parentRange.endOffset - range.endOffset
        }
        else -> false
      }
    }

    /**
     * Offset of the fence's opening backtick run, for the same two shapes [isInMiddleOfUnCollapsedFence]
     * recognizes -- the offset [MarkdownCodeFenceUtils.getIndent] would use if this were already a real
     * [org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence]. Null outside those two shapes.
     */
    private fun fenceStartOffset(element: PsiElement?): Int? {
      return when {
        element == null -> null
        element.hasType(MarkdownTokenTypes.CODE_FENCE_START) -> element.textRange.startOffset
        element.hasType(MarkdownTokenTypes.TEXT) && element.parent.hasType(MarkdownElementTypes.CODE_SPAN) -> element.parent.textRange.startOffset
        else -> null
      }
    }
  }
}
