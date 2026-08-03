package com.intellij.python.hatch.sdk.evolution

import com.intellij.openapi.module.Module
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.hatch.HatchConfiguration
import com.intellij.python.hatch.PyHatchBundle
import com.intellij.python.hatch.getHatchService
import com.intellij.python.hatch.icons.PythonHatchIcons
import com.intellij.python.sdk.backend.evolution.EvoSdk
import com.intellij.python.sdk.backend.evolution.EvoSelectSdkProvider
import com.intellij.python.sdk.backend.evolution.evoWarning
import com.intellij.python.sdk.backend.evolution.resolvePythonExecutable
import com.intellij.python.sdk.backend.evolution.toSelectLeaf
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.getOrNull
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.toFileSystem
import javax.swing.Icon

internal class HatchSelectSdkProvider : EvoSelectSdkProvider {
  override val id: String get() = "Hatch"
  override val label: String get() = "Hatch"
  override val icon: Icon get() = PythonHatchIcons.Logo

  override suspend fun loadSections(module: Module): EvoLoadResultDto {
    val fileSystem = localEel.toFileSystem()
    val hatchExecutablePath = HatchConfiguration.getOrDetectHatchExecutablePath(fileSystem).getOrNull()
                              ?: return evoWarning(PyHatchBundle.message("evolution.hatch.executable.is.not.found"))
    val leaves = findEnvironments(module, fileSystem).map { it.toSelectLeaf() }
    return EvoLoadResultDto.Ok(listOf(EvoSectionDto(label = hatchExecutablePath.toString(), leaves = leaves)))
  }
}

private suspend fun findEnvironments(module: Module, fileSystem: FileSystem<PathHolder.Eel>): List<EvoSdk> {
  val hatchService = module.getHatchService(fileSystem).getOrNull() ?: return emptyList()
  val environments = hatchService.findVirtualEnvironments().getOrNull() ?: return emptyList()
  return environments.map { env ->
    EvoSdk(
      icon = PythonHatchIcons.Logo,
      name = env.hatchEnvironment.name,
      pythonBinaryPath = env.pythonVirtualEnvironment?.pythonHomePath?.path?.resolvePythonExecutable(),
    )
  }
}
