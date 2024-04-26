// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.settings

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

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