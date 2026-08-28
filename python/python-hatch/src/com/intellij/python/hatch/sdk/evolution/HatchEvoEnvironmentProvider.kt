package com.intellij.python.hatch.sdk.evolution

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.hatch.HatchPyTool
import com.intellij.python.hatch.HatchVirtualEnvironment
import com.intellij.python.hatch.PyHatchBundle
import com.intellij.python.hatch.cli.HatchEnv
import com.intellij.python.hatch.getHatchService
import com.intellij.python.hatch.impl.HATCH_TOOL_ID
import com.intellij.python.hatch.resolveHatchWorkingDirectory
import com.intellij.python.pytools.PyTool
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.EvoRecreateSpec
import com.intellij.python.sdk.backend.evolution.EvoToolContext
import com.intellij.python.sdk.backend.evolution.NO_VERSION
import com.intellij.python.sdk.backend.evolution.PyToolEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.evoCreateEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoWarning
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoRecreateDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.hatch.sdk.createSdk
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.impl.resolvePythonBinary
import java.nio.file.Path
import com.intellij.python.hatch.impl.sdk.HatchSdkFlavor
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor

private const val ENVS_KEY: String = "hatch.virtualEnvironments"

internal class HatchEvoEnvironmentProvider : PyToolEvoEnvironmentProvider() {
  override val tool: PyTool get() = HatchPyTool.getInstance()
  override val toolId: ToolId get() = HATCH_TOOL_ID

  /** An interpreter of this node's environments carries this flavor, which is what names this node as the active one. */
  override val sdkFlavor: Class<out PythonSdkFlavor<*>> get() = HatchSdkFlavor::class.java

  override suspend fun loadSections(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
    val hatchService = pyProject.module.getHatchService(fileSystem).getOrNull()
                       ?: return evoWarning(PyHatchBundle.message("evolution.hatch.executable.is.not.found"))
    val environments = hatchService.findVirtualEnvironments().getOrNull() ?: return EvoLoadResultDto.Ok(emptyList())
    val leaves = environments.map { env ->
      val binary = env.pythonVirtualEnvironment?.pythonHomePath?.path?.resolvePythonBinary()
      // Materialized env → select it; a declared-but-not-created env → create it on click (token = env name).
      // A not-created env carries its declared `python` option, which [decorate] turns into the version picker.
      if (binary != null) evoEnvLeaf(title = env.hatchEnvironment.name, pythonBinary = binary, icon = icon)
      else evoCreateEnvLeaf(title = env.hatchEnvironment.name, token = env.hatchEnvironment.name, icon = icon,
                            name = env.hatchEnvironment.pythonSpec?.versionSpecifiers)
    }
    return EvoLoadResultDto.Ok(listOf(EvoSectionDto(label = null, leaves = leaves)))
  }

  /** Adopts an existing hatch env as a hatch-typed SDK, matched to the declared env whose interpreter is [homePath]. */
  override suspend fun createSdkForExistingEnv(context: EvoToolContext, homePath: Path): PyResult<Sdk> {
    val module = context.pyProject.module
    val hatchService = module.getHatchService(context.fileSystem).getOr { return it }
    val env = hatchService.findVirtualEnvironments().getOr { return it }.firstOrNull { candidate ->
      candidate.pythonVirtualEnvironment?.pythonHomePath?.path?.resolvePythonBinary()?.toString() == homePath.toString()
    } ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.env.not.found", homePath.toString()))
    // A missing working directory is not fatal: hatch is driven from the module's base dir in that case, as before.
    val workingDir = resolveHatchWorkingDirectory(context.pyProject.project, module).getOrNull() ?: context.pyProject.baseDir
    return env.createSdk(workingDir, context.fileSystem, null)
  }

  /**
   * Materializes a declared-but-not-created hatch env, then builds its SDK.
   *
   * Two shapes reach here, told apart by whether `folder` is set — the version picker [decorate] attaches puts the env
   * name in `folder` and the chosen base Python in `token`, while a row with no picker carries only the env name in
   * `token` and falls back to whichever system Python is found first.
   */
  override suspend fun createSdkForNewEnv(context: EvoToolContext, ref: PyInterpreterRef.CreateEnv): PyResult<Sdk> {
    val envName = ref.folder ?: ref.token
    val hatchService = context.pyProject.module.getHatchService(context.fileSystem).getOr { return it }
    val hatchEnv = hatchService.findVirtualEnvironments().getOr { return it }
                     .firstOrNull { it.hatchEnvironment.name == envName }?.hatchEnvironment
                   ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.env.not.found", envName))
    val eelApi = context.fileSystem.eelDescriptor?.toEelApi() ?: localEel
    val basePython = if (ref.folder != null) {
      // The picker put a base interpreter path in the token; an unparseable one is a broken round-trip, not user input.
      ref.token.toNioPathOrNull()
      ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.base.python.not.found", ref.token))
    }
    else {
      SystemPythonService().findSystemPythons(eelApi).firstOrNull()?.pythonBinary
      ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.base.python.not.found", ""))
    }
    val venv = hatchService.createVirtualEnvironment(PathHolder.Eel(basePython), envName).getOr { return it }
    return HatchVirtualEnvironment(hatchEnv, venv).createSdk(hatchService.getWorkingDirectoryPath(), context.fileSystem, null)
  }

  /**
   * A materialized hatch env may be rebuilt on any base Python its own declaration admits.
   *
   * Asked per row rather than per node, because the answer really is per row: each declared env carries its own
   * `python` specifier, which constrains its bases and no other env's. An existing-env leaf does not carry that
   * specifier — only a not-yet-created one does, in its ref — so the env is looked up again here, behind the request's
   * cache so one node load makes one hatch call.
   */
  override suspend fun recreateSpecFor(context: EvoToolContext, leaf: EvoLeafDto): EvoRecreateDto? {
    val homePath = (leaf.ref as? PyInterpreterRef.DetectedPath)?.homePath ?: return null
    val env = envFor(context, homePath) ?: return null
    val options = context.systemPythonOptions(env.hatchEnvironment.pythonSpec?.versionSpecifiers).takeIf { it.isNotEmpty() }
                  ?: return null
    return EvoRecreateDto(options = options, canSyncPackages = true)
  }

  /**
   * Removes the env through hatch and lets hatch create it again, then fills it when asked.
   *
   * Hatch keeps its own record of an env beside the directory, so the directory alone is not the environment — this
   * goes through `hatch env remove` rather than deleting the folder.
   *
   * [HatchEnv.RemoveResult.CantRemoveActiveEnvironment] is the one outcome where nothing was destroyed, so it ends this
   * with a message instead of building over an environment that is still there. The other two non-removals mean the env
   * was already gone, which is exactly the state the create step wants.
   */
  override suspend fun recreateEnv(context: EvoToolContext, homePath: Path, spec: EvoRecreateSpec): PyResult<Sdk> {
    val hatchService = context.pyProject.module.getHatchService(context.fileSystem).getOr { return it }
    val env = envFor(context, homePath.toString())
              ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.env.not.found", homePath.toString()))
    val envName = env.hatchEnvironment.name
    when (hatchService.removeVirtualEnvironment(envName).getOr { return it }) {
      HatchEnv.RemoveResult.CantRemoveActiveEnvironment ->
        return PyResult.localizedError(PySdkBundle.message("evolution.error.hatch.env.active", envName))
      HatchEnv.RemoveResult.Removed, HatchEnv.RemoveResult.NotExists, HatchEnv.RemoveResult.NotDefinedInConfig -> Unit
    }
    val basePython = spec.baseToken.toNioPathOrNull()
                     ?: return PyResult.localizedError(PySdkBundle.message("evolution.error.base.python.not.found", spec.baseToken))
    val venv = hatchService.createVirtualEnvironment(PathHolder.Eel(basePython), envName).getOr { return it }
    if (spec.syncPackages) hatchService.syncDependencies(envName).getOr { return it }
    return HatchVirtualEnvironment(env.hatchEnvironment, venv)
      .createSdk(hatchService.getWorkingDirectoryPath(), context.fileSystem, null)
  }

  /** The declared env whose interpreter is [homePath], or null when none is — the lookup [createSdkForExistingEnv] does. */
  private suspend fun envFor(context: EvoToolContext, homePath: String): HatchVirtualEnvironment<PathHolder.Eel>? {
    val hatchService = context.pyProject.module.getHatchService(context.fileSystem).getOrNull() ?: return null
    return context.cached(ENVS_KEY) { hatchService.findVirtualEnvironments().getOrNull().orEmpty() }
      .firstOrNull { it.pythonVirtualEnvironment?.pythonHomePath?.path?.resolvePythonBinary()?.toString() == homePath }
  }

  /**
   * Turns each declared-but-not-created env into a Python-version picker, so the user chooses the base Python instead of
   * silently getting whichever one is found first.
   *
   * Such a row has no interpreter to probe, so it also gets an explicit "n/a" in the version column the materialized
   * envs fill — which is what makes the two kinds of row distinguishable at a glance.
   */
  override suspend fun decorate(context: EvoToolContext, result: EvoLoadResultDto): EvoLoadResultDto {
    if (result !is EvoLoadResultDto.Ok) return result
    return result.copy(sections = result.sections.map { section ->
      section.copy(leaves = section.leaves.map { leaf -> leaf.withBasePythonPicker(context) })
    })
  }

  /**
   * This row with its base-Python picker attached, or unchanged when it creates no environment.
   *
   * An env's `python` option says which interpreters can build it, so the picker offers those alone. A matrix env is the
   * common case: `test.py3.11` declares `3.11`, and every other version on the machine would be the wrong answer. A
   * range such as `>=3.8` keeps every version it admits.
   *
   * The constraint is the env's own, so it replaces the project's `requires-python` and the 3.8 floor of the general
   * list rather than joining them — an env that asks for 2.7 offers 2.7.
   *
   * An env that declares no version keeps the full list, as before. See
   * [com.intellij.python.hatch.cli.HatchPythonSpec.versionSpecifiers] for the options that constrain no version.
   */
  private suspend fun EvoLeafDto.withBasePythonPicker(context: EvoToolContext): EvoLeafDto {
    val create = ref as? PyInterpreterRef.CreateEnv ?: return this
    // `name` holds the env's declared version specifier — this provider put it there in loadSections.
    val options = context.systemPythonOptions(create.name)
    // Declared in pyproject.toml but nothing on the machine to build it from: creating it would fail, so the row says
    // so up front instead of looking creatable.
    if (options.isEmpty()) {
      return copy(unavailable = PySdkBundle.message("evolution.error.no.base.python"), secondaryText = secondaryText ?: NO_VERSION)
    }
    return copy(createVersions = options, secondaryText = secondaryText ?: NO_VERSION)
  }
}
