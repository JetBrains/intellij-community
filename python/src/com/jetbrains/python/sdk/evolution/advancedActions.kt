package com.jetbrains.python.sdk.evolution

import com.intellij.icons.AllIcons
import com.intellij.python.sdk.ui.PySdkUiBundle
import com.intellij.python.sdk.ui.evolution.sdk.EvoModuleSdk
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeLazyNodeElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeLeafElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeSection
import com.intellij.util.SlowOperations
import com.jetbrains.python.Result
import com.intellij.python.sdk.ui.evolution.ui.EvoSelectSdkProvider
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.collectAddInterpreterActions
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.sdk.switchToSdk


private class AdvancedSelectSdkProvider() : EvoSelectSdkProvider {
  override fun getTreeElement(evoModuleSdk: EvoModuleSdk) = EvoTreeLazyNodeElement(
    text = PySdkUiBundle.message("evo.sdk.status.bar.popup.select.advanced"),
    icon = AllIcons.Toolwindows.ToolWindowInternal
  ) {

    val baseIdeActions = collectAddInterpreterActions(ModuleOrProject.ModuleAndProject(evoModuleSdk.module)) { sdk ->
      SlowOperations.knownIssue("PY-76167").use {
        switchToSdk(evoModuleSdk.module, sdk, evoModuleSdk.module.pythonSdk)
      }
    }

    val section = EvoTreeSection(
      label = null,
      elements = baseIdeActions.map { action ->
        EvoTreeLeafElement(action)
      }
    )

    Result.success(listOf(section))
  }
}