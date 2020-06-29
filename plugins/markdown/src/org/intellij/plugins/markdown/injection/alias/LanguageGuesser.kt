// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.injection.alias

import com.intellij.lang.Language
import com.intellij.lexer.EmbeddedTokenTypesProvider
import com.intellij.openapi.util.text.StringUtil
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider

/**
 * Service capable of guessing IntelliJ Language from info string.
 *
 * It would perform search by aliases, by ids and in [EmbeddedTokenTypesProvider]
 * case-insensitively
 */
internal object LanguageGuesser {
  val customProviders: List<CodeFenceLanguageProvider>
    get() = CodeFenceLanguageProvider.EP_NAME.extensionList

  /**
   * Guess IntelliJ Language from Markdown info-string.
   * It may either be lower-cased id or some of the aliases.
   *
   * @return IntelliJ Language if it was found
   */
  fun guessLanguage(value: String): Language? {
    val name = LanguageInfoString.findId(value)

    for (provider in customProviders) {
      val lang = provider.getLanguageByInfoString(name)
      if (lang != null) return lang
    }

    val lower = StringUtil.toLowerCase(name)
    val candidate = Language.findLanguageByID(lower)
    if (candidate != null) return candidate

    for (language in Language.getRegisteredLanguages()) {
      if (StringUtil.toLowerCase(language.id) == lower) return language
    }

    for (provider in EmbeddedTokenTypesProvider.EXTENSION_POINT_NAME.extensionList) {
      if (provider.name.equals(name, ignoreCase = true)) return provider.elementType.language
    }

    return null
  }
}