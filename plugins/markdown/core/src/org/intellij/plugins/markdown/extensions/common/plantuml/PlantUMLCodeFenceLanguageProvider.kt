// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.plantuml

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.fileTypes.UnknownFileType
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider

class PlantUMLCodeFenceLanguageProvider: CodeFenceLanguageProvider {
  override fun getLanguageByInfoString(infoString: String): Language? {
    return when {
      isPlantUmlInfoString(infoString) -> obtainPlantUmlLanguage()
      else -> null
    }
  }

  override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): List<LookupElement> {
    val language = obtainPlantUmlLanguage()
    val icon = language.associatedFileType?.icon
    return aliases.map { LookupElementBuilder.create(it).withIcon(icon) }
  }

  companion object {
    private val aliases = listOf(
      "plantuml",
      "puml"
    )

    internal fun isPlantUmlInfoString(infoString: String): Boolean {
      return infoString.lowercase() in aliases
    }

    internal fun obtainPlantUmlLanguage(): Language {
      val registry = FileTypeRegistry.getInstance()
      val fileType = aliases.asSequence().map(registry::getFileTypeByExtension).filterIsInstance<LanguageFileType>().firstOrNull()
      if (fileType != null && fileType != UnknownFileType.INSTANCE) {
        return fileType.language
      }
      return PlainTextLanguage.INSTANCE
    }
  }
}
