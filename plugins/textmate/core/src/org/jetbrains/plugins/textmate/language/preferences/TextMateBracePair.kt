package org.jetbrains.plugins.textmate.language.preferences

import org.jetbrains.plugins.textmate.language.TextMateStandardTokenType
import java.util.EnumSet

data class TextMateBracePair(val left: String, val right: String)

data class TextMateAutoClosingPair(val left: String, val right: String, val notIn: EnumSet<TextMateStandardTokenType>?)