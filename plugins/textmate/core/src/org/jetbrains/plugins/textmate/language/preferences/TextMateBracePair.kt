package org.jetbrains.plugins.textmate.language.preferences

import org.jetbrains.plugins.textmate.language.TextMateStandardTokenType
import java.util.EnumSet

data class TextMateBracePair(val left: CharSequence, val right: CharSequence)

data class TextMateAutoClosingPair(val left: CharSequence, val right: CharSequence, val notIn: EnumSet<TextMateStandardTokenType>?)