package org.intellij.plugins.markdown.xml

import com.intellij.lang.Language
import com.intellij.lexer.EmbeddedTokenTypesProvider
import org.intellij.plugins.markdown.injection.aliases.AdditionalFenceLanguageSuggester

internal class EmbeddedTokensSuggester: AdditionalFenceLanguageSuggester {
  override fun suggestLanguage(name: String): Language? {
    val embeddedTokenTypesProviders = EmbeddedTokenTypesProvider.getProviders().asSequence()
    val providers = embeddedTokenTypesProviders.filter { it.name.equals(name, ignoreCase = true) }
    return providers.map { it.elementType.language }.firstOrNull()
  }
}
