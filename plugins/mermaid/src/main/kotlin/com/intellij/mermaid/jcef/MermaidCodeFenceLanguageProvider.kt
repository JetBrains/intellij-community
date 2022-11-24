package com.intellij.mermaid.jcef

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.Language
import com.intellij.mermaid.lang.MermaidLanguage
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider

class MermaidCodeFenceLanguageProvider: CodeFenceLanguageProvider {
  override fun getLanguageByInfoString(infoString: String): Language? {
    return when (infoString) {
      MERMAID -> MermaidLanguage
      else -> null
    }
  }

  override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): List<LookupElement> {
    val element = LookupElementBuilder.create(MERMAID)
      .withTypeText(MermaidLanguage.displayName, true)
      .withIcon(MermaidLanguage.associatedFileType?.icon)
    return listOf(element)
  }

  companion object {
    private const val MERMAID = "mermaid"
  }
}
