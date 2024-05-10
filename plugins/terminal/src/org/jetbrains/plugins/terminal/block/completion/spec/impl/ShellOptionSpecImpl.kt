// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import com.intellij.terminal.completion.spec.ShellArgumentSpec
import com.intellij.terminal.completion.spec.ShellOptionSpec
import org.jetbrains.annotations.Nls
import java.util.function.Supplier
import javax.swing.Icon

internal class ShellOptionSpecImpl(
  names: List<String>,
  override val displayName: String?,
  descriptionSupplier: Supplier<@Nls String>?,
  override val insertValue: String?,
  override val priority: Int,
  override val isRequired: Boolean,
  override val isPersistent: Boolean,
  override val separator: String?,
  override val repeatTimes: Int,
  override val exclusiveOn: List<String>,
  override val dependsOn: List<String>,
  override val arguments: List<ShellArgumentSpec>
) : ShellCompletionSuggestionBase(names, descriptionSupplier), ShellOptionSpec {
  // the icon of option will be specified in the completion logic
  override val icon: Icon? = null

  init {
    if (priority !in 0..100) {
      error("Priority must be between 0 and 100")
    }
  }

  override fun toString(): String {
    return "ShellOptionSpecImpl(names=$names, displayName=$displayName, isRequired=$isRequired, isPersistent=$isPersistent, separator=$separator, repeatTimes=$repeatTimes, exclusiveOn=$exclusiveOn, dependsOn=$dependsOn, insertValue=$insertValue, priority=$priority)"
  }
}