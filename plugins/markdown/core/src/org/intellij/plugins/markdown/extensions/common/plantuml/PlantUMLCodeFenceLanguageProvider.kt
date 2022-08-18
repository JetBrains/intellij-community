// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.plantuml

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.Language
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider

class PlantUMLCodeFenceLanguageProvider: CodeFenceLanguageProvider {
  override fun getLanguageByInfoString(infoString: String): Language? {
    return when {
      infoString in aliases -> PlantUMLLanguage.INSTANCE
      else -> null
    }
  }

  override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): List<LookupElement> {
    return aliases.map { LookupElementBuilder.create(it) }
  }

  companion object {
    private val aliases = listOf(
      "plantuml",
      "puml"
    )
  }
}
