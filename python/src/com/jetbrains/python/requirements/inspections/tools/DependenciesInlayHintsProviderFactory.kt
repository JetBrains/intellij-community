// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.tools

import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory
import com.intellij.codeInsight.hints.declarative.InlayProviderInfo
import com.intellij.lang.Language
import com.jetbrains.python.PyBundle
import com.jetbrains.python.inspections.dependencies.DependenciesPsiProviderData

internal class DependenciesInlayHintsProviderFactory : InlayHintsProviderFactory {
  override fun getProvidersForLanguage(language: Language): List<InlayProviderInfo> =
    if (language in getSupportedLanguages()) {
      listOf(
        InlayProviderInfo(
          provider = DependenciesInlayHintsProvider(),
          providerId = "python.requirements.inlay.${language.id}",
          options = emptySet(),
          isEnabledByDefault = true,
          providerName = PyBundle.message("INLAY.requirements.inlay.name"),
        )
      )
    }
    else {
      emptyList()
    }

  override fun getSupportedLanguages(): Set<Language> =
    DependenciesPsiProviderData.languages

  override fun getProviderInfo(
    language: Language,
    providerId: String,
  ): InlayProviderInfo? =
    getProvidersForLanguage(language).find { it.providerId == providerId }
}
