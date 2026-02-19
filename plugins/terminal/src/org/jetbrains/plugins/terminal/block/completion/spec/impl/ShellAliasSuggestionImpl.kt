// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.completion.spec.ShellAliasSuggestion
import com.intellij.terminal.completion.spec.ShellSuggestionType
import org.jetbrains.plugins.terminal.TerminalBundle
import javax.swing.Icon

internal class ShellAliasSuggestionImpl(
  override val name: String,
  override val aliasValue: String,
  override val type: ShellSuggestionType = ShellSuggestionType.COMMAND,
  override val displayName: String? = null,
  description: String? = null,
  override val insertValue: String? = null,
  override val priority: Int = 50,
  override val icon: Icon? = null,
  override val prefixReplacementIndex: Int = 0,
  override val isHidden: Boolean = false,
  override val shouldEscape: Boolean = true,
) : ShellAliasSuggestion {
  override val description: String by lazy {
    description ?: TerminalBundle.message("doc.popup.alias.text", aliasValue)
  }

  override fun toString(): String {
    return "ShellAliasSuggestionImpl(name='$name', alias='$aliasValue', type=$type, displayName=$displayName, insertValue=$insertValue, priority=$priority, icon=$icon)"
  }
}
