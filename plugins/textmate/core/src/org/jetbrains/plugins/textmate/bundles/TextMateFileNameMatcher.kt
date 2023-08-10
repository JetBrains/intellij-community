package org.jetbrains.plugins.textmate.bundles

sealed class TextMateFileNameMatcher {
  data class Extension(val extension: String) : TextMateFileNameMatcher()
  data class Name(val fileName: String) : TextMateFileNameMatcher()
  data class Pattern(val pattern: String) : TextMateFileNameMatcher()
}
