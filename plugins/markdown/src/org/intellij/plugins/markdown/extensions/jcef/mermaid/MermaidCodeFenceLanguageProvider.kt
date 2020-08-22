// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef.mermaid

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.Language
import org.intellij.plugins.markdown.injection.CodeFenceLanguageProvider

internal class MermaidCodeFenceLanguageProvider : CodeFenceLanguageProvider {
  private val MERMAID = "mermaid"

  override fun getLanguageByInfoString(infoString: String): Language? =
    MermaidLanguage.INSTANCE.takeIf { infoString == MERMAID }

  override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): List<LookupElement> =
    listOf(LookupElementBuilder.create(MERMAID))
}
