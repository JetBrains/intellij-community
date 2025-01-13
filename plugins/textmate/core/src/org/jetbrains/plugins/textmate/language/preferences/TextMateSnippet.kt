package org.jetbrains.plugins.textmate.language.preferences

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.textmate.language.TextMateScopeSelectorOwner

data class TextMateSnippet(
  val key: String,
  val content: String,
  override val scopeSelector: CharSequence,
  val name: String,
  val description: @Nls(capitalization = Nls.Capitalization.Sentence) String,
  val settingsId: String,
) : TextMateScopeSelectorOwner
