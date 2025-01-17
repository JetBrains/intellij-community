package org.jetbrains.plugins.textmate.language

enum class TextMateStandardTokenType {
  STRING,
  COMMENT;

  internal fun mask(): Int {
    return 1 shl ordinal
  }
}