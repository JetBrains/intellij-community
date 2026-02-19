// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.session

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.session.impl.dto.TerminalSizeDto
import org.jetbrains.plugins.terminal.session.impl.dto.toDto
import org.jetbrains.plugins.terminal.session.impl.dto.toTermSize

/**
 * DTO for [org.jetbrains.plugins.terminal.ShellStartupOptions].
 * Only main fields are supported now, so it can't fully transfer the original options state.
 */
@ApiStatus.Internal
@Serializable
data class ShellStartupOptionsDto(
  val workingDirectory: String?,
  val shellCommand: List<String>?,
  @Contextual
  val initialTermSize: TerminalSizeDto?,
  val envVariables: Map<String, String>,
)

@ApiStatus.Internal
fun ShellStartupOptions.toDto(): ShellStartupOptionsDto {
  return ShellStartupOptionsDto(
    workingDirectory,
    shellCommand,
    initialTermSize?.toDto(),
    envVariables,
  )
}

@ApiStatus.Internal
fun ShellStartupOptionsDto.toShellStartupOptions(): ShellStartupOptions {
  return ShellStartupOptions.Builder()
    .workingDirectory(workingDirectory)
    .shellCommand(shellCommand)
    .initialTermSize(initialTermSize?.toTermSize())
    .envVariables(envVariables)
    .build()
}