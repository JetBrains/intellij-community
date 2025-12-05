// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface ShellDataGeneratorProcessOptions {
  val executable: String
  val args: List<String>
  val workingDirectory: String
  val env: Map<String, String>
}

@ApiStatus.Internal
data class ShellDataGeneratorProcessOptionsImpl(
  override val executable: String,
  override val args: List<String>,
  override val workingDirectory: String,
  override val env: Map<String, String>,
) : ShellDataGeneratorProcessOptions