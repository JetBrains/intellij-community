// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.session.rpc

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.getOrNull
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentLaunchSpecDto
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentMode
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAvailableAgentDto

internal object TerminalAgentResolver {
  suspend fun listAvailableAgents(project: Project): List<TerminalAvailableAgentDto> {
    val eelDescriptor = project.getEelDescriptor()
    if (!isAgentsResolutionAvailable(eelDescriptor)) return emptyList()

    val eelApi = eelDescriptor.toEelApi()
    return TerminalAgent.getAllTerminalAgents().mapNotNull { agent ->
      when {
        findBinaryPath(agent, eelApi) != null -> TerminalAvailableAgentDto(agent.agentKey, TerminalAgentMode.RUN)
        agent.getInstallCommand(eelDescriptor.osFamily) != null -> TerminalAvailableAgentDto(agent.agentKey, TerminalAgentMode.INSTALL_AND_RUN)
        else -> null
      }
    }
  }

  suspend fun resolveLaunchSpec(project: Project, agentKey: TerminalAgent.AgentKey): TerminalAgentLaunchSpecDto? {
    val eelDescriptor = project.getEelDescriptor()
    if (!isAgentsResolutionAvailable(eelDescriptor)) return null

    val agent = TerminalAgent.findByKey(agentKey) ?: return null
    val binaryPath = findBinaryPath(agent, eelDescriptor.toEelApi())
    if (binaryPath != null) {
      return TerminalAgentLaunchSpecDto(command = listOf(binaryPath), mode = TerminalAgentMode.RUN)
    }

    val installCommand = agent.getInstallCommand(eelDescriptor.osFamily) ?: return null
    return TerminalAgentLaunchSpecDto(command = installCommand, mode = TerminalAgentMode.INSTALL_AND_RUN)
  }

  private fun isAgentsResolutionAvailable(eelDescriptor: EelDescriptor): Boolean {
    // Allow only for the local env at the moment.
    // WSL and Docker require special handling, so they are postponed.
    return eelDescriptor is LocalEelDescriptor
  }

  @VisibleForTesting
  suspend fun findBinaryPath(
    terminalAgent: TerminalAgent,
    eelApi: EelApi,
  ): String? {
    return when (eelApi.descriptor.osFamily) {
      EelOsFamily.Windows -> findWindowsBinaryPath(terminalAgent, eelApi)
      EelOsFamily.Posix -> findPosixBinaryPath(terminalAgent, eelApi)
    }
  }

  private suspend fun findPosixBinaryPath(
    terminalAgent: TerminalAgent,
    eelApi: EelApi,
  ): String? {
    return eelApi.exec.findExeFilesInPath(terminalAgent.binaryName).firstOrNull()?.toString()
           ?: findPosixKnownLocationBinaryPath(terminalAgent, eelApi)
  }

  private suspend fun findWindowsBinaryPath(
    terminalAgent: TerminalAgent,
    eelApi: EelApi,
  ): String? {
    for (extension in terminalAgent.windowsExecutableExtensions) {
      val path = eelApi.exec.findExeFilesInPath("${terminalAgent.binaryName}.$extension").firstOrNull()
      if (path != null) {
        return path.toString()
      }
    }
    return findWindowsKnownLocationBinaryPath(terminalAgent, eelApi)
  }

  private suspend fun findPosixKnownLocationBinaryPath(
    terminalAgent: TerminalAgent,
    eelApi: EelApi,
  ): String? {
    for (candidate in terminalAgent.posixKnownLocationCandidates) {
      val folder = resolveKnownLocationCandidate(candidate, eelApi) ?: continue
      val path = folder.resolve(terminalAgent.binaryName)
      if (eelApi.fs.isRegularFile(path)) {
        return path.toString()
      }
    }
    return null
  }

  private suspend fun findWindowsKnownLocationBinaryPath(
    terminalAgent: TerminalAgent,
    eelApi: EelApi,
  ): String? {
    for (candidate in terminalAgent.windowsKnownLocationCandidates) {
      val folder = resolveKnownLocationCandidate(candidate, eelApi) ?: continue
      for (extension in terminalAgent.windowsExecutableExtensions) {
        val path = folder.resolve("${terminalAgent.binaryName}.$extension")
        if (eelApi.fs.isRegularFile(path)) {
          return path.toString()
        }
      }
    }
    return null
  }

  private fun resolveKnownLocationCandidate(candidate: String, eelApi: EelApi): EelPath? {
    return if (candidate.startsWith(HOME_MARKER)) {
      val homeRelative = candidate.removePrefix(HOME_MARKER).removePrefix("/").removeSuffix("\\")
      eelApi.fs.user.home.resolve(homeRelative)
    }
    else {
      runCatching {
        EelPath.parse(candidate, eelApi.descriptor)
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
@TestOnly
suspend fun findTerminalAgentBinaryPath(
  terminalAgent: TerminalAgent,
  eelApi: EelApi,
): String? {
  return TerminalAgentResolver.findBinaryPath(terminalAgent, eelApi)
}
