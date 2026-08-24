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
import com.intellij.python.hatch.getHatchService
import com.intellij.python.hatch.impl.HATCH_TOOL_ID
import com.intellij.python.hatch.resolveHatchWorkingDirectory
import com.intellij.python.pytools.PyTool
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.EvoPyProject
import com.intellij.python.sdk.backend.evolution.EvoToolContext
import com.intellij.python.sdk.backend.evolution.NO_VERSION
import com.intellij.python.sdk.backend.evolution.PyToolEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.evoCreateEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoWarning
import com.intellij.python.sdk.backend.evolution.resolvePythonExecutable
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.intellij.python.sdk.common.evolution.PyInterpreterRef
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.hatch.sdk.createSdk
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.impl.PySdkBundle
import java.nio.file.Path

internal class HatchEvoEnvironmentProvider : PyToolEvoEnvironmentProvider() {
  override val tool: PyTool get() = HatchPyTool.getInstance()
  override val toolId: ToolId get() = HATCH_TOOL_ID

  override suspend fun loadSections(pyProject: EvoPyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
    val hatchService = pyProject.module.getHatchService(fileSystem).getOrNull()
                       ?: return evoWarning(PyHatchBundle.message("evolution.hatch.executable.is.not.found"))
    val environments = hatchService.findVirtualEnvironments().getOrNull() ?: return EvoLoadResultDto.Ok(emptyList())
    val leaves = environments.map { env ->
      val binary = env.pythonVirtualEnvironment?.pythonHomePath?.path?.resolvePythonExecutable()
      // Materialized env → select it; a declared-but-not-created env → create it on click (token = env name).
      if (binary != null) evoEnvLeaf(title = env.hatchEnvironment.name, pythonBinary = binary, icon = icon)
      else evoCreateEnvLeaf(title = env.hatchEnvironment.name, token = env.hatchEnvironment.name, icon = icon)
    }
    return EvoLoadResultDto.Ok(listOf(EvoSectionDto(label = null, leaves = leaves)))
  }

  /** Adopts an existing hatch env as a hatch-typed SDK, matched to the declared env whose interpreter is [homePath]. */
  override suspend fun createSdkForExistingEnv(context: EvoToolContext, homePath: Path): PyResult<Sdk> {
    val module = context.pyProject.module
    val hatchService = module.getHatchService(context.fileSystem).getOr { return it }
    val env = hatchService.findVirtualEnvironments().getOr { return it }.firstOrNull { candidate ->
      candidate.pythonVirtualEnvironment?.pythonHomePath?.path?.resolvePythonExecutable()?.toString() == homePath.toString()
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
   * Turns each declared-but-not-created env into a Python-version picker, so the user chooses the base Python instead of
   * silently getting whichever one is found first.
   *
   * Such a row has no interpreter to probe, so it also gets an explicit "n/a" in the version column the materialized
   * envs fill — which is what makes the two kinds of row distinguishable at a glance.
   */
  override suspend fun decorate(context: EvoToolContext, result: EvoLoadResultDto): EvoLoadResultDto {
    if (result !is EvoLoadResultDto.Ok) return result
    val options = context.systemPythonOptions()
    return result.copy(sections = result.sections.map { section ->
      section.copy(leaves = section.leaves.map { leaf ->
        when {
          leaf.ref !is PyInterpreterRef.CreateEnv -> leaf
          // Declared in pyproject.toml but nothing on the machine to build it from: creating it would fail, so the row
          // says so up front instead of looking creatable.
          options.isEmpty() -> leaf.copy(
            unavailable = PySdkBundle.message("evolution.error.no.base.python"),
            secondaryText = leaf.secondaryText ?: NO_VERSION,
          )
          else -> leaf.copy(createVersions = options, secondaryText = leaf.secondaryText ?: NO_VERSION)
        }
      })
    })
  }
}
