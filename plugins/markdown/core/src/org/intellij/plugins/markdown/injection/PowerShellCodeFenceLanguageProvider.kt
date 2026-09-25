// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.injection

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.Language
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageAliases

internal class PowerShellCodeFenceLanguageProvider : CodeFenceLanguageProvider {
  override fun getLanguageByInfoString(infoString: String): Language? {
    val name = infoString.trim().takeWhile { !it.isWhitespace() }
    return PowerShellRunnerLanguage.takeIf { CodeFenceLanguageAliases.findRegisteredEntry(name) == "PowerShell" }
  }

  override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): List<LookupElement> = emptyList()

  private object PowerShellRunnerLanguage : Language("PowerShell", false)
}
