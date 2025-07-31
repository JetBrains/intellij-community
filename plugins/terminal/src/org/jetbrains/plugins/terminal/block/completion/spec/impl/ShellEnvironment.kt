// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class ShellEnvironment(
  val envs: List<String> = emptyList(),
  val keywords: List<String> = emptyList(),
  val builtins: List<String> = emptyList(),
  val functions: List<String> = emptyList(),
  val commands: List<String> = emptyList(),
  val aliases: Map<String, String> = emptyMap(),
)