package com.jetbrains.python.conda.sdk.evolution

import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.where
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.intellij.python.sdk.ui.evolution.AddNewEnvAction
import com.intellij.python.sdk.ui.evolution.SelectEnvAction
import com.intellij.python.sdk.ui.evolution.sdk.EvoModuleSdk
import com.intellij.python.sdk.ui.evolution.sdk.EvoSdk
import com.intellij.python.sdk.ui.evolution.sdk.EvoWarning
import com.intellij.python.sdk.ui.evolution.sdk.resolvePythonExecutable
import com.intellij.python.sdk.ui.evolution.ui.EvoSelectSdkProvider
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeLazyNodeElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeLeafElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeSection
import com.jetbrains.python.icons.PythonIcons
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

private class CondaSelectSdkProvider() : EvoSelectSdkProvider {
  override fun getTreeElement(evoModuleSdk: EvoModuleSdk) = EvoTreeLazyNodeElement("Conda", PythonIcons.Python.Anaconda) {
    val condaExecutablePath = findCondaExecutablePath().getOr {
      return@EvoTreeLazyNodeElement it
    }

    val condaPath = condaExecutablePath.toString()
    val baseCondaPath = condaExecutablePath.parent.parent.toString()
    val environments = findEnvironments(evoModuleSdk.module).getOr {
      return@EvoTreeLazyNodeElement it
    }
    val byFolders = environments.groupBy { baseCondaPath.commonPrefixWith(it.pythonBinaryPath.toString()) }
    print(byFolders)
    val environmentActions = environments.map { evoSdk -> EvoTreeLeafElement(SelectEnvAction(evoSdk)) }
    val sections = listOf(
      EvoTreeSection(ListSeparator(condaPath), environmentActions),
      EvoTreeSection(null, EvoTreeLeafElement(AddNewEnvAction())),
    )
    Result.success(sections)
  }
}


private fun EelApi.getCondaCommand(): String = when (platform) {
  is EelPlatform.Windows -> "conda.bat"
  else -> "conda"
}

private suspend fun findCondaExecutablePath(eelApi: EelApi = localEel): Result<Path, PyError> {
  val condaExecutablePath = eelApi.exec.where(eelApi.getCondaCommand())?.asNioPath()
  return when {
    condaExecutablePath?.exists() == true -> Result.success(condaExecutablePath)
    else -> Result.failure(EvoWarning("conda not found"))
  }
}

private suspend fun findEnvironments(module: Module): Result<List<EvoSdk>, PyError> {
  val condaExecutablePath = findCondaExecutablePath().getOr { return it }
  val stdout = ExecService().execGetStdout(condaExecutablePath, Args("env", "list")).getOr {
    return it
  }

  val environments = stdout.trim().lines()
    .filter { !it.startsWith('#') }
    .map { line ->
      val (name, pathStr) = line.split("\\s+".toRegex())
      val path = Path.of(pathStr)
      val realName = name.takeIf { it.isNotBlank() } ?: path.name
      realName to path
    }

  val evoSdks = environments.map { (name, path) ->
    val sdkHomePath = path.resolvePythonExecutable()
    EvoSdk(icon = PythonIcons.Python.Anaconda, name = name, pythonBinaryPath = sdkHomePath)
  }
  return Result.success(evoSdks)
}
