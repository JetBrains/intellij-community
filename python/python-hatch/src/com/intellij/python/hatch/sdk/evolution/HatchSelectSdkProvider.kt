package com.intellij.python.hatch.sdk.evolution

import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.python.hatch.HatchConfiguration
import com.intellij.python.hatch.getHatchService
import com.intellij.python.hatch.icons.PythonHatchIcons
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.intellij.python.sdk.ui.evolution.sdk.EvoModuleSdk
import com.intellij.python.sdk.ui.evolution.sdk.EvoSdk
import com.intellij.python.sdk.ui.evolution.sdk.resolvePythonExecutable
import com.intellij.python.sdk.ui.evolution.SelectEnvAction
import com.intellij.python.sdk.ui.evolution.tool.hatch.sdk.HatchEvoSdkManager.buildEvoSdk
import com.intellij.python.sdk.ui.evolution.ui.EvoSelectSdkProvider
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeLazyNodeElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeLeafElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeSection

private class HatchSelectSdkProvider : EvoSelectSdkProvider {
  override fun getTreeElement(evoModuleSdk: EvoModuleSdk): EvoTreeElement =
    EvoTreeLazyNodeElement("Hatch", PythonHatchIcons.Logo) {
      val hatchExecutablePath = HatchConfiguration.getOrDetectHatchExecutablePath().getOr {
        return@EvoTreeLazyNodeElement it // Result.failure(IllegalAccessError("Hatch (https://hatch.pypa.io) executable is not found")
      }

      val environments = findEnvironments(evoModuleSdk.module).getOr {
        return@EvoTreeLazyNodeElement it
      }
      val environmentActions = environments.map { evoSdk -> EvoTreeLeafElement(SelectEnvAction(evoSdk)) }
      val sections = listOf(
        EvoTreeSection(ListSeparator(hatchExecutablePath.toString()), environmentActions),
      )
      //Result.failure(IllegalAccessError("Hatch (https://hatch.pypa.io) executable is not found"))
      Result.Companion.success(sections)
    }
}

private suspend fun findEnvironments(module: Module): Result<List<EvoSdk>, PyError> {
  val hatchService = module.getHatchService().getOr { return it }
  val environments = hatchService.findVirtualEnvironments().getOr { return it }
  val evoSdks = environments.map { env ->
    buildEvoSdk(
      env.pythonVirtualEnvironment?.pythonHomePath?.resolvePythonExecutable(),
      env.hatchEnvironment.name
    )
  }
  return Result.success(evoSdks)
}
