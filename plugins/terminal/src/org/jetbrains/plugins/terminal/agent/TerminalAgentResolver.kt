// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.agent

import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.annotations.NativePath
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.listDirectoryWithAttrs
import com.intellij.platform.eel.getOrNull
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.util.PathUtil
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentLaunchSpecDto
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAgentMode
import org.jetbrains.plugins.terminal.agent.rpc.TerminalAvailableAgentDto

@ApiStatus.Internal
object TerminalAgentResolver {
  suspend fun listAvailableAgents(project: Project): List<TerminalAvailableAgentDto> {
    val eelDescriptor = project.getEelDescriptor()
    if (!isAgentsResolutionAvailable(eelDescriptor)) return emptyList()

    val eelApi = eelDescriptor.toEelApi()
    return coroutineScope {
      withContext(Dispatchers.IO) {
        // Resolve each agent in parallel because checking sequentially can be slow for remote environments with big latency.
        TerminalAgent.getAllTerminalAgents().map { agent ->
          async {
            getAvailableAgent(agent, eelApi)
          }
        }.awaitAll().filterNotNull()
      }
    }
  }

  private suspend fun getAvailableAgent(agent: TerminalAgent, eelApi: EelApi): TerminalAvailableAgentDto? {
    return when {
      findBinaryPath(agent, eelApi) != null -> {
        TerminalAvailableAgentDto(agent.agentKey, TerminalAgentMode.RUN)
      }
      ApplicationInfoEx.getInstanceEx().isVendorJetBrains && agent.getInstallCommand(eelApi.descriptor.osFamily) != null -> {
        TerminalAvailableAgentDto(agent.agentKey, TerminalAgentMode.INSTALL_AND_RUN)
      }
      else -> null
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
    // Allow only for the local env and RemDev scenario at the moment.
    // WSL and Docker require special handling, so they are postponed.
    return eelDescriptor is LocalEelDescriptor || IdeProductMode.isFrontend
  }

  suspend fun findBinaryPath(
    terminalAgent: TerminalAgent,
    eelApi: EelApi,
  ): @NativePath String? {
    return withContext(Dispatchers.IO) {
      when (eelApi.descriptor.osFamily) {
        EelOsFamily.Windows -> findWindowsBinaryPath(terminalAgent, eelApi)
        EelOsFamily.Posix -> findPosixBinaryPath(terminalAgent, eelApi)
      }
    }
  }

  private suspend fun findPosixBinaryPath(
    terminalAgent: TerminalAgent,
    eelApi: EelApi,
  ): String? = coroutineScope {
    // Launch PATH and known-location lookups in parallel because checking sequentially can be slow for remote environments with big latency.
    val pathLookup: Deferred<EelPath?> = async {
      eelApi.exec.findExeFilesInPath(terminalAgent.binaryName).firstOrNull()
    }
    val knownLocationLookups: List<Deferred<EelPath?>> = terminalAgent.posixKnownLocationCandidates.map { candidate ->
      async {
        findExistingBinaryPath(eelApi, terminalAgent.binaryName, candidate)
      }
    }
    (listOf(pathLookup) + knownLocationLookups).awaitAll().firstNotNullOfOrNull { it }?.toString()
  }

  private suspend fun findWindowsBinaryPath(
    terminalAgent: TerminalAgent,
    eelApi: EelApi,
  ): String? = coroutineScope {
    // Launch PATH and known-location lookups in parallel because checking sequentially can be slow for remote environments with big latency.
    val pathLookup: Deferred<EelPath?> = async {
      // Returned list should contain files with extensions as well
      val paths = eelApi.exec.findExeFilesInPath(terminalAgent.binaryName)
      terminalAgent.windowsExecutableExtensions.firstNotNullOfOrNull { extension ->
        paths.findPathWithExtension(extension)
      }
    }
    val knownLocationLookups: List<Deferred<EelPath?>> = terminalAgent.windowsKnownLocationCandidates.map { location ->
      async {
        findExistingPathWithExtension(eelApi, terminalAgent.binaryName, location, terminalAgent.windowsExecutableExtensions)
      }
    }
    (listOf(pathLookup) + knownLocationLookups).awaitAll().firstNotNullOfOrNull { it }?.toString()
  }

  private suspend fun findExistingPathWithExtension(
    eelApi: EelApi,
    binaryName: String,
    location: String,
    possibleExtensions: List<String>,
  ): EelPath? {
    check(eelApi.descriptor.osFamily == EelOsFamily.Windows) { "Only for Windows: assumes that file system is case-insensitive" }

    val locationPath = resolveKnownLocationCandidate(location, eelApi) ?: return null
    val paths: List<EelPath> = eelApi.fs.listDirectoryWithAttrs(locationPath).resolveAndFollow().eelIt()
      .getOrNull()
      .orEmpty()
      .mapNotNull { (fileName, fileInfo) ->
        val extension = PathUtil.getFileExtension(fileName)
        val fileNameWithoutExt = if (extension != null) fileName.removeSuffix(".$extension") else fileName
        if (fileNameWithoutExt.equals(binaryName, ignoreCase = true) && fileInfo.type is EelFileInfo.Type.Regular) {
          locationPath.resolve(fileName)
        }
        else null
      }
    return possibleExtensions.firstNotNullOfOrNull { extension ->
      paths.findPathWithExtension(extension)
    }
  }

  private fun List<EelPath>.findPathWithExtension(extension: String): EelPath? {
    return find { path ->
      val pathExt = PathUtil.getFileExtension(path.fileName)
      pathExt != null && pathExt.equals(extension, ignoreCase = true)
    }
  }

  private suspend fun findExistingBinaryPath(
    eelApi: EelApi,
    binaryName: String,
    location: String,
    extension: String? = null,
  ): EelPath? {
    val folder = resolveKnownLocationCandidate(location, eelApi) ?: return null
    val name = if (extension != null) "$binaryName.$extension" else binaryName
    val path = folder.resolve(name)
    return if (eelApi.fs.isRegularFile(path)) path else null
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
    return stat(path, EelFileSystemApi.SymlinkPolicy.RESOLVE_AND_FOLLOW).getOrNull()?.type is EelFileInfo.Type.Regular
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
