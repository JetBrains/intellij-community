// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.completion

import org.jetbrains.plugins.terminal.exp.BlockTerminalSession

interface DataProviderCommand<T> {
  val functionName: String
  val parameters: List<String>
  val defaultResult: T

  fun isAvailable(session: BlockTerminalSession): Boolean
  fun parseResult(result: String): T
}