package org.jetbrains.plugins.textmate.language.preferences

import org.jetbrains.plugins.textmate.language.TextMateScopeSelectorOwner

data class Preferences(
  override val scopeSelector: CharSequence,
  val highlightingPairs: MutableSet<TextMateBracePair>?,
  val smartTypingPairs: MutableSet<TextMateAutoClosingPair>?,
  val surroundingPairs: MutableSet<TextMateBracePair>?,
  val autoCloseBefore: String?,
  val indentationRules: IndentationRules,
  val onEnterRules: MutableSet<OnEnterRule>?,
) : TextMateScopeSelectorOwner