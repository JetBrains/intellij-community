package com.intellij.python.sdk.ui.evolution.tool.conda.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.ui.evolution.sdk.EvoSdk
import com.intellij.python.sdk.ui.evolution.sdk.EvoSdkProvider
import com.intellij.python.sdk.ui.icons.PythonSdkUIIcons
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.packaging.findCondaExecutableRelativeToEnv
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
object CondaEvoSdkManager  {
  fun buildEvoSdk(pythonBinaryPath: PythonBinary): EvoSdk? {
    val condaExecutablePath = findCondaExecutableRelativeToEnv(pythonBinaryPath)
                              ?: return null
    val condaEnvsPath = condaExecutablePath.resolve("../../envs")
    val name = condaEnvsPath.relativize(pythonBinaryPath.resolve("")).toString()

    return EvoSdk(
      icon = PythonSdkUIIcons.Tools.Anaconda,
      name = name,
      pythonBinaryPath = pythonBinaryPath,
    )
  }
}

internal object CondaEvoSdkProvider : EvoSdkProvider {
  override fun parsePySdk(module: Module, sdk: Sdk): EvoSdk? {
    val pythonBinaryPath = sdk.homePath?.let { Path.of(it) } ?: return null
    val evoSdk = CondaEvoSdkManager.buildEvoSdk(pythonBinaryPath)

    return evoSdk
  }
}