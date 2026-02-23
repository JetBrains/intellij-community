// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.python.common.tools.ToolId
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue

suspend fun Module.findPythonVirtualEnvironments(): List<PythonBinary> {
  val venvsInModule = this.baseDir?.let {
    withContext(Dispatchers.IO) {
      VirtualEnvReader().findVenvsInDir(it.toNioPath())
    }
  } ?: emptyList()

  return venvsInModule
}


/**
 * Used on directory opening with an attempt to configure suitable Python interpreter
 * (mentioned below as sdk configurator).
 *
 * Used with an attempt to suggest suitable Python interpreter
 * or try setup and register it in case of headless mode if no interpreter is specified.
 */
@ApiStatus.Internal
interface PyProjectSdkConfigurationExtension {
  companion object {
    private val EP_NAME: ExtensionPointName<PyProjectSdkConfigurationExtension> = ExtensionPointName.create("Pythonid.projectSdkConfigurationExtension")
    private val CONCURRENCY_LIMIT = Semaphore(permits = 5)

    /**
     * EPs associated by tool id
     */
    fun createMap(): Map<ToolId, PyProjectSdkConfigurationExtension> = EP_NAME.extensionList.associateBy { it.toolId }

    /**
     * We return all configurators in a sorted order. The order is determined by extensions order, but existing environments have a
     * higher priority. That means we first have all existing envs, and only after SDK creators that extensions can manage.
     */
    suspend fun findAllSortedForModule(module: Module, venvsInModule: List<PythonBinary>): List<CreateSdkInfoWithTool> {
      return EP_NAME.extensionsIfPointIsRegistered
        .concurrentMapNotNull { e -> e.checkEnvironmentAndPrepareSdkCreator(module, venvsInModule)?.let { CreateSdkInfoWithTool(it, e.toolId) } }
        .sortedBy { it.createSdkInfo }
    }

    suspend fun findAllSortedForModule(module: Module): List<CreateSdkInfoWithTool> {
      return findAllSortedForModule(module, module.findPythonVirtualEnvironments())
    }

    private suspend fun <A, B> Iterable<A>.concurrentMapNotNull(f: suspend (A) -> B?): List<B> = coroutineScope {
      map {
        async {
          CONCURRENCY_LIMIT.withPermit { f(it) }
        }
      }.awaitAll().filterNotNull()
    }
  }

  val toolId: ToolId

  /**
   * Discovers whether this extension can provide a Python SDK for the given module and prepares a creator for it.
   *
   * This function is executed on a background thread and may perform I/O-intensive checks such as
   * reading project files (for example, pyproject.toml, Pipfile, requirements.txt, environment.yml), probing the
   * file system, or invoking external tools (poetry/hatch/pipenv/uv/etc.). No SDK must be created or registered here.
   * Instead, the method returns a [CreateSdkInfo] descriptor that encapsulates:
   * - user-facing labels (intentionName) and tool metadata (toolInfo), and
   * - a suspendable sdkCreator that will create and register the SDK when executed by the caller
   *   (see [PyProjectSdkConfiguration.setSdkUsingCreateSdkInfo]).
   *
   * Return value semantics:
   * - Existing environment found: return a CreateSdkInfo.ExistingEnv whose creator simply registers the discovered SDK.
   * - No environment yet, but can be created: return a CreateSdkInfo.WillCreateEnv whose creator performs the creation
   *   (and optional user confirmation) and registers the SDK.
   * - Tool is not applicable, or configuration cannot proceed (missing binaries, incompatible project, errors): return null.
   *   Implementations are responsible for showing any user-facing error notifications when they decide to return null.
   *
   * The default ordering prefers existing environments over newly created ones; see CreateSdkInfo.compareTo.
   *
   * @param module module to inspect and derive configuration from
   * @return descriptor to create/register a suitable SDK, or null if this extension cannot configure the project
   */
  @CheckReturnValue
  suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo?

  /**
   * Returns this extension as a [PyProjectTomlConfigurationExtension] when a tool supports configuring with
   * pyproject.toml, or null otherwise.
   *
   * Callers that need to skip pyproject.toml validation should do it using
   * [PyProjectTomlConfigurationExtension.createSdkWithoutPyProjectTomlChecks].
   */
  fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension?
}

/**
 * [createSdkInfo] with [toolId] that created it
 */
data class CreateSdkInfoWithTool(val createSdkInfo: CreateSdkInfo, val toolId: ToolId)

@ApiStatus.Internal
val VENV_TOOL_ID: ToolId = ToolId("Venv")

@ApiStatus.Internal
val CONDA_TOOL_ID: ToolId = ToolId("Conda")

@ApiStatus.Internal
val PIPENV_TOOL_ID: ToolId = ToolId("pipenv")
