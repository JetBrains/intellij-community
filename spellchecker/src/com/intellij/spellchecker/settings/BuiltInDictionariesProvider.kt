// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.settings

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point for providing built-in spellchecking dictionaries
 * that should be displayed and can be edited in `Custom dictionaries` table in spellchecker options page.
 */
@ApiStatus.Internal
interface BuiltInDictionariesProvider {
  fun getDictionaries(): List<BuiltInDictionary>

  companion object {
    val EP_NAME = ExtensionPointName.create<BuiltInDictionariesProvider>("com.intellij.spellchecker.builtInDictionariesProvider")

    @JvmStatic
    fun getAll(): List<BuiltInDictionariesProvider> {
      return EP_NAME.extensionList
    }
  }
}