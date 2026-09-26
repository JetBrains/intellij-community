package org.jetbrains.plugins.textmate.language.preferences

import org.jetbrains.plugins.textmate.language.TextMateStandardTokenType

data class TextMateBracePair(val left: String, val right: String)

data class TextMateAutoClosingPair(val left: CharSequence, val right: CharSequence, private val notIn: Int) {
  fun notIn(type: TextMateStandardTokenType): Boolean {
    return notIn.and(type.mask()) != 0
  }
}