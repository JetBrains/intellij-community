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

  private fun findLanguage(value: String, registeredLanguages: Collection<Language>): Language? {
    val entry = CodeFenceLanguageAliases.findRegisteredEntry(value) ?: value
    val registered = registeredLanguages.find { it.id.equals(entry, ignoreCase = true) }
    if (registered != null) {
      return registered
    }
    val additionalSuggesters = suggestersExtensionPoint.extensionList.asSequence()
    return additionalSuggesters.map { it.suggestLanguage(entry) }.firstOrNull()
  }

  private fun findLanguage(value: String): Language? {
    val registeredLanguages = Language.getRegisteredLanguages()
    val exactMatch = findLanguage(value, registeredLanguages)
    if (exactMatch != null) {
      return exactMatch
    }
    var index = value.lastIndexOf(' ')
    while (index != -1) {
      val nameWithoutCustomizations = value.substring(0, index)
      val language = findLanguage(nameWithoutCustomizations, registeredLanguages)
      if (language != null) {
        return language
      }
      index = value.lastIndexOf(' ', startIndex = (index - 1).coerceAtLeast(0))
    }
    return null
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
    // Custom providers should handle customizations by themselves
    for (provider in customProviders) {
      val lang = provider.getLanguageByInfoString(value)
      if (lang != null) {
        return lang
      }
    }
    val name = value.lowercase()
    return findLanguage(name)
  }
}
