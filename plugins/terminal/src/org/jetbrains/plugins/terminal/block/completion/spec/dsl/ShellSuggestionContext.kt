// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellSuggestionContext {
  val names: List<@NonNls String>
  var displayName: String?
  var description: Supplier<@Nls String>?
  var insertValue: String?
  var priority: Int
}