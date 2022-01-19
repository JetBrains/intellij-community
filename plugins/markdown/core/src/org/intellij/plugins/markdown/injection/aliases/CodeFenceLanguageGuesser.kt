// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.injection.aliases

import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.lexer.EmbeddedTokenTypesProvider
import com.intellij.openapi.util.text.StringUtil
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider
import org.jetbrains.annotations.ApiStatus

/**
 * Service capable of guessing IntelliJ Language from info string.
 *
 * It would perform search by aliases, by ids and in [EmbeddedTokenTypesProvider]
 * case-insensitively
 */
@ApiStatus.Internal
object CodeFenceLanguageGuesser {
  val customProviders: List<CodeFenceLanguageProvider>
    get() = CodeFenceLanguageProvider.EP_NAME.extensionList

  /**
   * Guess IntelliJ Language from Markdown info-string.
   * It may either be lower-cased id or some of the aliases.
   *
   * Language is guaranteed to be safely injectable
   *
   * @return IntelliJ Language if it was found
   */
  @JvmStatic
  fun guessLanguageForInjection(value: String): Language? {
    return guessLanguage(value)?.takeIf { LanguageUtil.isInjectableLanguage(it) }
  }

  /**
   * Guess IntelliJ Language from Markdown info-string.
   * It may either be lower-cased id or some of the aliases.
   *
   * Note, that returned language can be non-injectable.
   * Consider using [guessLanguageForInjection]
   *
   * @return IntelliJ Language if it was found
   */
  @JvmStatic
  private fun guessLanguage(value: String): Language? {
    val name = CodeFenceLanguageAliases.findId(value)

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

    for (provider in EmbeddedTokenTypesProvider.getProviders()) {
      if (provider.name.equals(name, ignoreCase = true)) return provider.elementType.language
    }

    return null
  }
}
