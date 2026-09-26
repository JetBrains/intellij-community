// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.view.shellIntegration.history

/** Retains the most recent commands while preserving their chronological order. */
internal class LimitedHistoryCommands(private val limit: Int) {
  private val commands = ArrayDeque<String>()

  init {
    require(limit > 0) { "Command limit must be positive: $limit" }
  }

  fun add(command: String) {
    if (commands.size == limit) {
      commands.removeFirst()
    }
    commands.addLast(command)
  }

  fun toList(): List<String> = commands.toList()
}
