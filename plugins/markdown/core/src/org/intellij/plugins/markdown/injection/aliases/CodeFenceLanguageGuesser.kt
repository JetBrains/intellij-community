// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.injection.aliases

import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.extensions.ExtensionPointName
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object CodeFenceLanguageGuesser {
  val customProviders: List<CodeFenceLanguageProvider>
    get() = CodeFenceLanguageProvider.EP_NAME.extensionList

  private const val suggestersPointName = "org.intellij.markdown.additionalFenceLanguageSuggester"
  private val suggestersExtensionPoint = ExtensionPointName.create<AdditionalFenceLanguageSuggester>(suggestersPointName)

  fun guessLanguageForExecution(value: String): Language? {
    val trimmedValue = value.trim()
    for (provider in customProviders) {
      val language = provider.getLanguageByInfoString(trimmedValue)
      if (language != null) {
        return language
      }
    }
    return null
  }

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
    return guessLanguageWithExtensionForInjection(value)?.first
  }

  @JvmStatic
  fun guessLanguageWithExtensionForInjection(value: String): Pair<Language, String?>? {
    for (provider in customProviders) {
      val language = provider.getLanguageByInfoString(value)
      if (language != null && LanguageUtil.isInjectableLanguage(language)) {
        return Pair(language, provider.getExtensionByInfoString(value))
      }
    }
    return null
  }

  private fun findLanguage(value: String, registeredLanguages: Collection<Language>): Language? {
    val entry = CodeFenceLanguageAliases.findRegisteredEntry(value) ?: value
    val registered = registeredLanguages.find { it.id.equals(entry, ignoreCase = true) }
    if (registered != null) {
      return registered
    }
    val additionalSuggesters = suggestersExtensionPoint.extensionList.asSequence()
    return additionalSuggesters.map { it.suggestLanguage(entry) }.firstOrNull()
  }

  @JvmStatic
  @ApiStatus.Internal
  fun findLanguage(value: String): Language? {
    val registeredLanguages = Language.getRegisteredLanguages()
    var name = value.trim()
    while (true) {
      val language = findLanguage(name, registeredLanguages)
      if (language != null) {
        return language
      }
      val index = name.indexOfLast { it.isWhitespace() }
      if (index == -1) {
        return null
      }
      name = name.substring(0, index).trimEnd()
    }
  }
}