package com.jetbrains.python.conda.sdk.evolution

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.where
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.community.impl.conda.PyCondaBundle
import com.intellij.python.community.impl.conda.icons.PythonCommunityImplCondaIcons
import com.intellij.python.sdk.backend.evolution.EvoSdk
import com.intellij.python.sdk.backend.evolution.EvoSelectSdkProvider
import com.intellij.python.sdk.backend.evolution.evoWarning
import com.intellij.python.sdk.backend.evolution.resolvePythonExecutable
import com.intellij.python.sdk.backend.evolution.toSelectLeaf
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.findCondaExecutableRelativeToEnv
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.exists
import kotlin.io.path.name

internal class CondaSelectSdkProvider : EvoSelectSdkProvider {
  override val id: String get() = "Conda"
  override val label: String get() = "Conda"
  override val icon: Icon get() = PythonCommunityImplCondaIcons.Anaconda

  override suspend fun loadSections(module: Module): EvoLoadResultDto {
    val condaExecutablePath = findCondaExecutablePath()
                              ?: return evoWarning(PyCondaBundle.message("evolution.conda.executable.is.not.found"))
    val leaves = findEnvironments(condaExecutablePath).map { it.toSelectLeaf() }
    val sections = listOf(
      EvoSectionDto(label = condaExecutablePath.toString(), leaves = leaves),
      EvoSectionDto(label = null, leaves = emptyList(), addNew = true),
    )
    return EvoLoadResultDto.Ok(sections)
  }

  override suspend fun parseModuleSdk(module: Module, sdk: Sdk): EvoSdk? {
    val binary = sdk.homePath?.let { Path.of(it) } ?: return null
    val condaExecutablePath = findCondaExecutableRelativeToEnv(binary) ?: return null
    val name = condaExecutablePath.resolve("../../envs").relativize(binary.resolve("")).toString()
    return EvoSdk(icon = icon, name = name, pythonBinaryPath = binary)
  }

  private fun EelApi.getCondaCommand(): String = when (platform) {
    is EelPlatform.Windows -> "conda.bat"
    else -> "conda"
  }

  private suspend fun findCondaExecutablePath(eelApi: EelApi = localEel): Path? {
    val condaExecutablePath = eelApi.exec.where(eelApi.getCondaCommand())?.asNioPath()
    return condaExecutablePath?.takeIf { it.exists() }
  }

  private suspend fun findEnvironments(condaExecutablePath: Path): List<EvoSdk> {
    val stdout = ExecService().execGetStdout(condaExecutablePath, Args("env", "list")).getOrNull() ?: return emptyList()
    return stdout.trim().lines()
      .filter { !it.startsWith('#') }
      .mapNotNull { line ->
        val parts = line.split("\\s+".toRegex())
        val pathStr = parts.lastOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val path = Path.of(pathStr)
        val realName = parts.first().takeIf { it.isNotBlank() } ?: path.name
        EvoSdk(icon = icon, name = realName, pythonBinaryPath = path.resolvePythonExecutable())
      }
  }
}
