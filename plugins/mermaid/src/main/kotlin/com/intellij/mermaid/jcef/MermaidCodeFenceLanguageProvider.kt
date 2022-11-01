package com.intellij.mermaid.jcef

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.PlainTextLanguage
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider

class MermaidCodeFenceLanguageProvider : CodeFenceLanguageProvider {
  override fun getLanguageByInfoString(infoString: String): Language? {
    return when {
      isMermaidInfoString(infoString) -> obtainMermaidLanguage()
      else -> null
    }
  }

  override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): List<LookupElement> {
    val language = obtainMermaidLanguage()
    val lookupElement = LookupElementBuilder.create(MERMAID).withIcon(language.associatedFileType?.icon)
    return listOf(lookupElement)
  }

  companion object {
    private const val MERMAID = "mermaid"

    internal fun isMermaidInfoString(infoString: String): Boolean {
      return infoString == MERMAID
    }

    internal fun obtainMermaidLanguage(): Language {
      val existingLanguage = Language.findLanguageByID("Mermaid")
      return existingLanguage ?: PlainTextLanguage.INSTANCE
    }
  }
}
