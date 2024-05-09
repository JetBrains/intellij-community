package com.intellij.terminal.block.completion.spec

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellCompletionSuggestion {
  val names: List<@NonNls String>
  val type: ShellSuggestionType
  val displayName: @NonNls String?
  val description: @Nls String?
  val insertValue: String?
  val priority: Int
  val icon: Icon?
}