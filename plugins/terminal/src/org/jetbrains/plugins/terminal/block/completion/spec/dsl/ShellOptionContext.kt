// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellOptionContext : ShellSuggestionContext {
  var isPersistent: Boolean
  var isRequired: Boolean
  var separator: String?
  var repeatTimes: Int
  var exclusiveOn: List<String>
  var dependsOn: List<String>

  fun argument(content: ShellArgumentContext.() -> Unit = {})
}