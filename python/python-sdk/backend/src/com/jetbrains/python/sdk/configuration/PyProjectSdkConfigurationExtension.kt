// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.python.community.common.tools.ToolId
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
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
import org.jetbrains.annotations.VisibleForTesting

/**
 * The virtual environments that stand in [PyProject.baseDir].
 */
@ApiStatus.Internal
suspend fun PyProject.findPythonVirtualEnvironments(): List<PythonBinary> = withContext(Dispatchers.IO) {
  VirtualEnvReader().findVenvsInDir(baseDir)
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
    @VisibleForTesting
    val EP_NAME: ExtensionPointName<PyProjectSdkConfigurationExtension> =
      ExtensionPointName.create("Pythonid.projectSdkConfigurationExtension")
    private val CONCURRENCY_LIMIT = Semaphore(permits = 5)

    /**
     * Tool IDs of all the registered configurators.
     */
    val toolIds: List<ToolId>
      get() = EP_NAME.extensionsIfPointIsRegistered.map { it.toolId }

    /**
     * Dependency files that are supported by all the registered configurators.
     */
    val potentialDependencyFiles: Set<String>
      get() = EP_NAME.extensionsIfPointIsRegistered.foldRight(mutableSetOf()) { configurator, result ->
        result += configurator.potentialDependencyFiles
        result
      }

    /**
     * EPs associated by tool id
     */
    fun createMap(): Map<ToolId, PyProjectSdkConfigurationExtension> = EP_NAME.extensionList.associateBy { it.toolId }

    /**
     * The configurators for [pyProject] as [PySdkConfiguratorsCache] last found them, probing only when it has no recent
     * answer — **this is the entry point to use.** Every configurator may run its own tool to answer (see
     * [checkEnvironmentAndPrepareSdkCreator]), and the same question is asked from several unrelated features, so a
     * fresh probe per caller means running poetry, uv and the rest several times over for one project.
     *
     * Comes with the venvs the probe scanned, since a caller acting on an option usually needs those too.
     *
     * Use [findAllSorted] instead only where the answer must be true *right now*: under the SDK-configuration
     * lock, or straight after installing a tool — and in a test that changes the project on disk and asks again.
     */
    suspend fun findAllSortedCached(pyProject: PyProject): PyProjectConfigurators =
      PySdkConfiguratorsCache.getInstance(pyProject.project).get(pyProject)

    /**
     * Drops what [findAllSortedCached] remembers about [pyProject], for a caller that has just changed what a
     * probe would find — installing one of the tools, which turns a "will install" option into a creatable one.
     */
    fun invalidateCached(pyProject: PyProject) {
      PySdkConfiguratorsCache.getInstance(pyProject.project).invalidate(pyProject.residesOnModule)
    }

    /**
     * We return all configurators in a sorted order. The order is determined by extensions order, but existing environments have a
     * higher priority. That means we first have all existing envs, and only after SDK creators that extensions can manage.
     *
     * Probes every configurator on every call — see [findAllSortedCached] for the cached entry point, which is
     * what most callers want.
     */
    suspend fun findAllSorted(pyProject: PyProject, venvs: List<PythonBinary>): List<CreateSdkInfoWithTool> {
      val offered = EP_NAME.extensionsIfPointIsRegistered
        .concurrentMapNotNull { e ->
          e.checkEnvironmentAndPrepareSdkCreator(pyProject, venvs)?.let { e to CreateSdkInfoWithTool(it, e.toolId) }
        }
      // A configurator that owns this project's setup leaves no room for the others — see [isExclusiveFor]. Only one
      // that actually offered something can claim it, so a tool that is missing from the machine blanks no list.
      val claimed = offered.filter { (extension, _) -> extension.isExclusiveFor(pyProject) }
      return claimed.ifEmpty { offered }.map { it.second }.sortedBy { it.createSdkInfo }
    }

    suspend fun findAllSorted(pyProject: PyProject): List<CreateSdkInfoWithTool> {
      return findAllSorted(pyProject, pyProject.findPythonVirtualEnvironments())
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
  val potentialDependencyFiles: Set<String>

  /**
   * Whether this configurator owns the setup of [pyProject] outright, so that no other configurator's option applies.
   *
   * `false` for almost everything: a project can usually be set up with whichever tool the machine has, and the
   * choice is the user's. `true` only where the project has already made that choice and another tool would build an
   * environment beside the one the project declares.
   *
   * A uv workspace is the case today. It declares one environment, at its root, and a poetry or plain-venv
   * environment made for a member is one uv ignores, along with every run configuration that uses it.
   *
   * [findAllSorted] keeps only the claimants when any configurator claims a project. Answer without running
   * the tool: this is asked for every configurator on the busiest path into them.
   */
  suspend fun isExclusiveFor(pyProject: PyProject): Boolean = false

  /**
   * Discovers whether this extension can provide a Python SDK for [pyProject] and prepares a creator for it.
   *
   * This function is executed on a background thread and may perform I/O-intensive checks such as
   * reading project files (for example, pyproject.toml, Pipfile, requirements.txt, environment.yml), probing the
   * file system, or invoking external tools (poetry/hatch/pipenv/uv/etc.). No SDK must be created or registered here.
   * Instead, the method returns a [CreateInterpreterInfo] descriptor that encapsulates:
   * - user-facing labels (intentionName) and tool metadata (toolInfo), and
   * - a suspendable sdkCreator that will create and register the SDK when executed by the caller
   *   (see [CreateInterpreterInfoWithInterpreterCreator.getInterpreterCreator]).
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
   * @param pyProject project to inspect and derive configuration from
   * @param venvs the virtual environments that stand in [PyProject.baseDir], as [findPythonVirtualEnvironments] found them
   * @return descriptor to create/register a suitable SDK, or null if this extension cannot configure the project
   */
  @CheckReturnValue
  suspend fun checkEnvironmentAndPrepareSdkCreator(pyProject: PyProject, venvs: List<PythonBinary>): CreateInterpreterInfo?

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
data class CreateSdkInfoWithToolBase<T>(val createSdkInfo: T, val toolId: ToolId)
typealias CreateSdkInfoWithTool = CreateSdkInfoWithToolBase<CreateInterpreterInfo>


@ApiStatus.Internal
val VENV_TOOL_ID: ToolId = ToolId("Venv")

@ApiStatus.Internal
val CONDA_TOOL_ID: ToolId = ToolId("Conda")

@ApiStatus.Internal
val PIPENV_TOOL_ID: ToolId = ToolId("pipenv")
