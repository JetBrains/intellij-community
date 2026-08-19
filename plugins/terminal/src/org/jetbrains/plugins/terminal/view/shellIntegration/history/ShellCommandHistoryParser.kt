// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration.history

import org.jetbrains.annotations.ApiStatus

/** Parses the raw contents of a shell history file into commands. */
@ApiStatus.Internal
interface ShellCommandHistoryParser {
  fun parse(content: ByteArray, commandLimit: Int): List<String>
}
