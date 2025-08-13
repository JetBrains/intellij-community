package com.jetbrains.python.uv.sdk.evolution

import com.intellij.openapi.ui.popup.ListSeparator
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.basePath
import com.intellij.python.sdk.ui.evolution.sdk.EvoModuleSdk
import com.intellij.python.sdk.ui.evolution.sdk.EvoSdk
import com.intellij.python.sdk.ui.evolution.AddNewEnvAction
import com.intellij.python.sdk.ui.evolution.SelectEnvAction
import com.intellij.python.sdk.ui.evolution.tool.pip.sdk.VenvEvoSdkManager
import com.intellij.python.sdk.ui.evolution.ui.EvoSelectSdkProvider
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeLazyNodeElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeLeafElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeSection
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import java.nio.file.Path
import kotlin.collections.component1
import kotlin.collections.component2


private class UvSelectSdkProvider() : EvoSelectSdkProvider {
  override fun getTreeElement(evoModuleSdk: EvoModuleSdk) = EvoTreeLazyNodeElement("uv", PythonIcons.UV) {
    getUvExecutable() ?: return@EvoTreeLazyNodeElement PyResult.localizedError("uv executable is not found")

    val environments = VenvEvoSdkManager.findEnvironments(evoModuleSdk.module).getOr {
      return@EvoTreeLazyNodeElement it
    }.map { it.copy(icon = PythonIcons.UV) }
    val envByFolders = environments.groupBy { it.pythonBinaryPath?.parent?.parent?.parent }.toMutableMap()
    envByFolders.putIfAbsent(
      evoModuleSdk.module.basePath?.let { Path.of(it) },
      listOf(EvoSdk(icon = PythonIcons.UV, name = ".venv", pythonBinaryPath = null))
    )
    val envSections = envByFolders.map { (basePath, sdks) ->
      val label = basePath?.toString() ?: "undefined"
      val leafs = sdks.map { evoSdk -> SelectEnvAction(evoSdk) }.map { action -> EvoTreeLeafElement(action) }
      EvoTreeSection(ListSeparator(label), leafs + EvoTreeLeafElement(AddNewEnvAction()))
    }

    val sections = buildList {
      addAll(envSections)
    }

    Result.success(sections)
  }
}