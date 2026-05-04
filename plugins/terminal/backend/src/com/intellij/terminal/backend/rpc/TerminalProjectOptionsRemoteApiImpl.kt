// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.settings.TerminalLocalOptions
import org.jetbrains.plugins.terminal.settings.impl.EnvironmentVariablesDataDto
import org.jetbrains.plugins.terminal.settings.impl.TerminalProjectOptionsDto
import org.jetbrains.plugins.terminal.settings.impl.TerminalProjectOptionsRemoteApi

internal class TerminalProjectOptionsRemoteApiImpl : TerminalProjectOptionsRemoteApi {
  override suspend fun getProjectOptions(projectId: ProjectId): TerminalProjectOptionsDto {
    val project = projectId.findProjectOrNull()
                  ?: return TerminalProjectOptionsDto(startingDirectory = null, shellPath = null, envData = null)

    val state = TerminalProjectOptionsProvider.getInstance(project).state

    val startingDirectory = state.startingDirectory
    // Project-level shell path takes precedence; fall back to the app-level shell path which can also be customized.
    val shellPath = state.shellPath ?: TerminalLocalOptions.getInstance().shellPath

    val envOptions = state.envDataOptions
    val envData = if (envOptions.envs.isEmpty() && envOptions.isPassParentEnvs) {
      null
    }
    else {
      EnvironmentVariablesDataDto(
        envs = LinkedHashMap(envOptions.envs),
        isPassParentEnvs = envOptions.isPassParentEnvs,
      )
    }

    return TerminalProjectOptionsDto(
      startingDirectory = startingDirectory,
      shellPath = shellPath,
      envData = envData,
    )
  }
}