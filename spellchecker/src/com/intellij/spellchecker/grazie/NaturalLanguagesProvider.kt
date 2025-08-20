// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.grazie

import ai.grazie.nlp.langs.Language
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.ScheduledForRemoval
@Deprecated("This interface won't be present in 2025.3")
interface NaturalLanguagesProvider {

  /**
   * Returns the set of enabled languages.
   */
  fun getEnabledLanguages(): Set<Language>

  companion object {
    private val EP_NAME = ExtensionPointName<NaturalLanguagesProvider>("com.intellij.spellchecker.languages")

    fun getEnabledLanguages(): Set<Language> = EP_NAME.extensionList
      .asSequence()
      .map { it.getEnabledLanguages() }
      .filter { it.isNotEmpty() }
      .flatten()
      .toSet()
  }
}