// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.injection

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import org.intellij.plugins.markdown.editor.CodeFenceLanguageListCompletionProvider.Companion.createLanguageIcon
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageAliases.findMainAlias
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser.findLanguage

internal class DefaultCodeFenceLanguageProvider: CodeFenceLanguageProvider {
  override fun getLanguageByInfoString(infoString: String): Language? = findLanguage(infoString.lowercase())

  override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): List<LookupElement> {
    val result = mutableListOf<LookupElement>()
    for (language in LanguageUtil.getInjectableLanguages()) {
      val alias = findMainAlias(language.id)
      val lookupElement = LookupElementBuilder.create(alias)
        .withIcon(createLanguageIcon(language))
        .withTypeText(language.displayName, true)
      result.add(lookupElement)
    }
    return result
  }
}
