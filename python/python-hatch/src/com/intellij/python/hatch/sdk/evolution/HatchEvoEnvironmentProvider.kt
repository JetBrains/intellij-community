package com.intellij.python.hatch.sdk.evolution

import com.intellij.python.hatch.HatchPyTool
import com.intellij.python.hatch.PyHatchBundle
import com.intellij.python.hatch.getHatchService
import com.intellij.python.hatch.icons.PythonHatchIcons
import com.intellij.python.pytools.resolveExecutable
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.evoCreateEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoWarning
import com.intellij.python.sdk.backend.evolution.resolvePythonExecutable
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.getOrNull
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import javax.swing.Icon

internal class HatchEvoEnvironmentProvider : PyEvoEnvironmentProvider {
  override val id: String get() = "Hatch"
  override val label: String get() = "Hatch"
  override val icon: Icon get() = PythonHatchIcons.Logo

  override suspend fun isAvailable(pyProject: PyProject, fileSystem: FileSystem<PathHolder.Eel>): Boolean =
    HatchPyTool.getInstance().resolveExecutable(fileSystem) != null

  override suspend fun loadSections(pyProject: PyProject, fileSystem: FileSystem<PathHolder.Eel>, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
    val hatchService = pyProject.residesOnModule.getHatchService(fileSystem).getOrNull()
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
}
