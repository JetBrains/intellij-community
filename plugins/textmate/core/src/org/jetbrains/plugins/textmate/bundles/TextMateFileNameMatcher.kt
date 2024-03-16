package org.jetbrains.plugins.textmate.bundles

sealed class TextMateFileNameMatcher {
  data class Extension(@JvmField val extension: String) : TextMateFileNameMatcher()
  data class Name(@JvmField val fileName: String) : TextMateFileNameMatcher()
  internal data class Pattern(@JvmField val pattern: String) : TextMateFileNameMatcher()
}
