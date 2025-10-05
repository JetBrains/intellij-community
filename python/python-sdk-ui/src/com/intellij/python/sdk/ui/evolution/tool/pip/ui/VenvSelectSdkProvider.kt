package com.intellij.python.sdk.ui.evolution.tool.pip.ui

import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.python.sdk.ui.evolution.AddNewEnvAction
import com.intellij.python.sdk.ui.evolution.SelectEnvAction
import com.intellij.python.sdk.ui.evolution.sdk.EvoModuleSdk
import com.intellij.python.sdk.ui.evolution.sdk.EvoSdk
import com.intellij.python.sdk.ui.evolution.tool.pip.sdk.VenvEvoSdkManager
import com.intellij.python.sdk.ui.evolution.ui.EvoSelectSdkProvider
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeLazyNodeElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeLeafElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeSection
import com.intellij.python.sdk.ui.icons.PythonSdkUIIcons
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.basePath
import java.nio.file.Path
import kotlin.collections.plus

private class VenvSelectSdkProvider() : EvoSelectSdkProvider {
  override fun getTreeElement(evoModuleSdk: EvoModuleSdk) = EvoTreeLazyNodeElement("pip", PythonSdkUIIcons.Tools.Pip) {
    val environments = VenvEvoSdkManager.findEnvironments(evoModuleSdk.module).getOr {
      return@EvoTreeLazyNodeElement it
    }
    val envByFolders = environments.groupBy { it.pythonBinaryPath?.parent?.parent?.parent }.toMutableMap()
    envByFolders.putIfAbsent(
      evoModuleSdk.module.basePath?.let { Path.of(it) },
      listOf(EvoSdk(icon = PythonSdkUIIcons.Tools.Pip, name = ".venv", pythonBinaryPath = null))
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

//class VenvEnvironmentActionGroup(val evoSdk: EvoSdk) : DefaultActionGroup(
//  evoSdk.getTitle(),
//  evoSdk.getDescription(),
//  evoSdk.getIcon()
//) {
//  init {
//    templatePresentation.isPopupGroup = true
//    templatePresentation.isPerformGroup = true
//    //templatePresentation.setKeepPopupOnPerform(KeepPopupOnPerform.Always)
//    add(object : AnAction({ "Second" }, { evoSdk.getDescription() }, evoSdk.getIcon()) {
//      override fun actionPerformed(e: AnActionEvent) {}
//    })
//  }
//}