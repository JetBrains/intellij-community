// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.psi.PsiElement
import com.intellij.ui.DeferredIconImpl
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageAliases.findMainAlias
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.util.hasType
import javax.swing.Icon

class CodeFenceLanguageListCompletionProvider: CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    for (provider in CodeFenceLanguageGuesser.customProviders) {
      val lookups = provider.getCompletionVariantsForInfoString(parameters)
      for (lookupElement in lookups) {
        val element = LookupElementDecorator.withInsertHandler(lookupElement) { context: InsertionContext, item: LookupElementDecorator<LookupElement> ->
          MyInsertHandler(parameters).handleInsert(context, item)
          lookupElement.handleInsert(context)
        }
        result.addElement(element)
      }
    }
    for (language in LanguageUtil.getInjectableLanguages()) {
      val alias = findMainAlias(language.id)
      val lookupElement = LookupElementBuilder.create(alias)
        .withIcon(createLanguageIcon(language))
        .withTypeText(language.displayName, true)
        .withInsertHandler(MyInsertHandler(parameters))
      result.addElement(lookupElement)
    }
  }

  private class MyInsertHandler(private val parameters: CompletionParameters): InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
      if (isInMiddleOfUnCollapsedFence(parameters.originalPosition, context.startOffset)) {
        context.document.insertString(context.tailOffset, "\n\n")
        context.editor.caretModel.moveCaretRelatively(1, 0, false, false, false)
      }
    }
  }

  companion object {
    @JvmStatic
    fun createLanguageIcon(language: Language): Icon {
      return DeferredIconImpl(null, language, true) { curLanguage: Language -> curLanguage.associatedFileType?.icon }
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
  }
}
