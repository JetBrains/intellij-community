// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.shell_integration

class CommandBlockIntegration(useCommandEndMarker: Boolean = false) {

  val commandEndMarker: String? = if (useCommandEndMarker) generateRandomCommandEndMarker() else null

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CommandBlockIntegration

    return commandEndMarker == other.commandEndMarker
  }

  override fun hashCode(): Int {
    return commandEndMarker?.hashCode() ?: 0
  }

  companion object {
    private fun generateRandomCommandEndMarker(): String {
      val alphanumeric: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
      return (1..COMMAND_END_MARKER_LENGTH).map { alphanumeric.random() }.joinToString("")
    }

    private const val COMMAND_END_MARKER_LENGTH = 64
  }
}