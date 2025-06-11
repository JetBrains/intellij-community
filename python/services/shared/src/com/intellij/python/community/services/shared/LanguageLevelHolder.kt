// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.shared

import com.jetbrains.python.psi.LanguageLevel

/**
 * Something with language level
 */
interface LanguageLevelHolder {
  val languageLevel: LanguageLevel
}