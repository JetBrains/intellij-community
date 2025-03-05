package org.jetbrains.plugins.textmate.language.preferences

import org.jetbrains.plugins.textmate.language.TextMateScopeSelectorOwner

data class Preferences(
  override val scopeSelector: CharSequence,
  val highlightingPairs: Set<TextMateBracePair>?,
  val smartTypingPairs: Set<TextMateAutoClosingPair>?,
  val surroundingPairs: Set<TextMateBracePair>?,
  val autoCloseBefore: String?,
  val indentationRules: IndentationRules,
  val onEnterRules: Set<OnEnterRule>?,
) : TextMateScopeSelectorOwner