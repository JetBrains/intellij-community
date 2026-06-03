package com.intellij.markdown.fenceInjector.textMate

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.Language
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser.findLanguage
import org.jetbrains.plugins.textmate.TextMateLanguage
import org.jetbrains.plugins.textmate.TextMateService
import org.jetbrains.plugins.textmate.bundles.TextMateFileNameMatcher

internal class TextMateFallbackLanguageProvider: CodeFenceLanguageProvider {

  override fun getLanguageByInfoString(infoString: String): Language? =
    if (findLanguage(infoString) == null) return TextMateLanguage.LANGUAGE else null

  override fun getExtensionByInfoString(infoString: String): String? {
    val normalizedId = infoString.lowercase()
    val service = TextMateService.getInstance()

    if (service.getLanguageDescriptorByExtension(normalizedId) != null) {
      return normalizedId
    }

    return service.fileNameMatcherToScopeNameMapping.asSequence()
      .mapNotNull { (matcher, scopeName) -> (matcher as? TextMateFileNameMatcher.Extension)?.let { it to scopeName } }
      .sortedBy { (matcher, _) -> matcher.extension.length }
      .firstOrNull { (_, scopeName) ->
        val scope = scopeName.toString()
        scope.equals("source.$normalizedId", ignoreCase = true) || scope.equals("text.$normalizedId", ignoreCase = true)
      }
      ?.first?.extension
  }

  override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): List<LookupElement> = emptyList()
}
