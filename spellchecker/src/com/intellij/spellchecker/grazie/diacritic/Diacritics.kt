// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.grazie.diacritic

import ai.grazie.nlp.utils.normalization.StripAccentsNormalizer
import com.intellij.util.io.IOUtil

object Diacritics {
  @JvmStatic
  fun equalsIgnoringDiacritics(word: String, diacritic: String): Boolean {
    if (!IOUtil.isAscii(diacritic)) {
      if (word.equals(StripAccentsNormalizer().normalize(diacritic), ignoreCase = true)) {
        return true
      }
      if (word.equals(replaceUmlauts(diacritic), ignoreCase = true)) {
        return true
      }
    }
    return false
  }

  private fun replaceUmlauts(suggestion: String): String {
    if (suggestion.any { it in "üöäßÜÖÄẞ" }) {
      //ü→ue, ö→oe, ä→ae, ß→ss
      return suggestion
        .replace("ü", "ue")
        .replace("ö", "oe")
        .replace("ä", "ae")
        .replace("ß", "ss")
        .replace("Ü", "Ue")
        .replace("Ö", "Oe")
        .replace("Ä", "Ae")
        .replace("ẞ", "Ss")
    }
    return suggestion
  }
}