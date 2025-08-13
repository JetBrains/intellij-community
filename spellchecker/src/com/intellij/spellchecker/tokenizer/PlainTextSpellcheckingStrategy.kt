// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.tokenizer

import com.intellij.openapi.util.registry.Registry

class PlainTextSpellcheckingStrategy: SpellcheckingStrategy() {
  override fun useTextLevelSpellchecking(): Boolean {
    return Registry.`is`("spellchecker.grazie.enabled")
  }
}