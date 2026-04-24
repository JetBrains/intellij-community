// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.backend.rpc

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.getOrNull
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentLaunchSpecDto
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentMode
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentsApi
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAvailableAgentDto

internal class TerminalAgentsApiImpl : TerminalAgentsApi {
  override suspend fun listAvailableAgents(projectId: ProjectId): List<TerminalAvailableAgentDto> {
    return TerminalAgentResolver.listAvailableAgents(projectId.findProject())
  }

  override suspend fun resolveLaunchSpec(projectId: ProjectId, agentKey: TerminalAgent.AgentKey): TerminalAgentLaunchSpecDto? {
    return TerminalAgentResolver.resolveLaunchSpec(projectId.findProject(), agentKey)
  }
}

private object TerminalAgentResolver {
  suspend fun listAvailableAgents(project: Project): List<TerminalAvailableAgentDto> {
    val context = createResolutionContext(project) ?: return emptyList()
    return TerminalAgent.getAllTerminalAgents().mapNotNull { agent ->
      when {
        findBinaryPath(agent, context) != null -> TerminalAvailableAgentDto(agent.agentKey, TerminalAgentMode.RUN)
        agent.getInstallCommand(context.osFamily) != null -> TerminalAvailableAgentDto(agent.agentKey, TerminalAgentMode.INSTALL_AND_RUN)
        else -> null
      }
    }
  }

  suspend fun resolveLaunchSpec(project: Project, agentKey: TerminalAgent.AgentKey): TerminalAgentLaunchSpecDto? {
    val agent = TerminalAgent.findByKey(agentKey) ?: return null
    val context = createResolutionContext(project) ?: return null
    val binaryPath = findBinaryPath(agent, context)
    if (binaryPath != null) {
      return TerminalAgentLaunchSpecDto(command = listOf(binaryPath), mode = TerminalAgentMode.RUN)
    }

    val installCommand = agent.getInstallCommand(context.osFamily) ?: return null
    return TerminalAgentLaunchSpecDto(command = installCommand, mode = TerminalAgentMode.INSTALL_AND_RUN)
  }

  private suspend fun createResolutionContext(project: Project): TerminalAgentResolutionContext? {
    val descriptor = project.getEelDescriptor()
    if (descriptor != LocalEelDescriptor) return null

    val eelApi = descriptor.toEelApi()
    val environment = if (eelApi.platform.isWindows) {
      try {
        eelApi.exec.environmentVariables().onlyActual(true).eelIt().await()
      }
      catch (ex: EelExecApi.EnvironmentVariablesException) {
        thisLogger().warn("Failed to fetch environment variables for terminal AI agent resolution", ex)
        emptyMap()
      }
    }
    else {
      emptyMap()
    }
    return TerminalAgentResolutionContext(
      eelApi = eelApi,
      osFamily = descriptor.osFamily,
      environment = environment,
    )
  }

  suspend fun findBinaryPath(
    terminalAgent: TerminalAgent,
    context: TerminalAgentResolutionContext,
  ): String? {
    return when (context.osFamily) {
      EelOsFamily.Windows -> findWindowsBinaryPath(terminalAgent, context)
      EelOsFamily.Posix -> findPosixBinaryPath(terminalAgent, context)
    }
  }

  private suspend fun findPosixBinaryPath(
    terminalAgent: TerminalAgent,
    context: TerminalAgentResolutionContext,
  ): String? {
    return context.eelApi.exec.findExeFilesInPath(terminalAgent.binaryName).firstOrNull()?.toString()
           ?: findPosixKnownLocationBinaryPath(terminalAgent, context)
  }

  private suspend fun findWindowsBinaryPath(
    terminalAgent: TerminalAgent,
    context: TerminalAgentResolutionContext,
  ): String? {
    for (extension in terminalAgent.windowsExecutableExtensions) {
      val path = context.eelApi.exec.findExeFilesInPath("${terminalAgent.binaryName}.$extension").firstOrNull()
      if (path != null) {
        return path.toString()
      }
    }
    return findWindowsKnownLocationBinaryPath(terminalAgent, context)
  }

  private suspend fun findPosixKnownLocationBinaryPath(
    terminalAgent: TerminalAgent,
    context: TerminalAgentResolutionContext,
  ): String? {
    for (candidate in terminalAgent.posixKnownLocationCandidates) {
      val folder = resolveKnownLocationCandidate(candidate, context) ?: continue
      val path = folder.resolve(terminalAgent.binaryName)
      if (context.eelApi.fs.isRegularFile(path)) {
        return path.toString()
      }
    }
    return null
  }

  private suspend fun findWindowsKnownLocationBinaryPath(
    terminalAgent: TerminalAgent,
    context: TerminalAgentResolutionContext,
  ): String? {
    for (candidate in terminalAgent.windowsKnownLocationCandidates) {
      val folder = resolveKnownLocationCandidate(candidate, context) ?: continue
      for (extension in terminalAgent.windowsExecutableExtensions) {
        val path = folder.resolve("${terminalAgent.binaryName}.$extension")
        if (context.eelApi.fs.isRegularFile(path)) {
          return path.toString()
        }
      }
    }
    return null
  }

  private fun resolveKnownLocationCandidate(candidate: String, context: TerminalAgentResolutionContext): EelPath? {
    return if (candidate.startsWith(HOME_MARKER)) {
      val homeRelative = candidate.removePrefix(HOME_MARKER).removePrefix("/").removeSuffix("\\")
      context.eelApi.fs.user.home.resolve(homeRelative)
    }
    else {
      runCatching {
        EelPath.parse(candidate, context.eelApi.descriptor)
      }.getOrElse { error ->
        thisLogger().error("Parsing candidate path: $candidate", error)
        null
      }
    }
  }

  private suspend fun EelFileSystemApi.isRegularFile(path: EelPath): Boolean {
    return stat(path, EelFileSystemApi.SymlinkPolicy.JUST_RESOLVE).getOrNull()?.type is EelFileInfo.Type.Regular
  }

  private const val HOME_MARKER: String = $$"$HOME"
}

@ApiStatus.Internal
@VisibleForTesting
data class TerminalAgentResolutionContext(
  val eelApi: EelApi,
  val osFamily: EelOsFamily,
  val environment: Map<String, String>,
)

@ApiStatus.Internal
@VisibleForTesting
suspend fun findTerminalAgentBinaryPath(
  terminalAgent: TerminalAgent,
  context: TerminalAgentResolutionContext,
): String? {
  return TerminalAgentResolver.findBinaryPath(terminalAgent, context)
}
