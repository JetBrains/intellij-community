package com.intellij.python.sdk.ui.evolution.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.python.sdk.ui.PySdkUiBundle
import com.intellij.python.sdk.ui.evolution.sdk.EvoModuleSdk
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeLazyNodeElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeLeafElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeSection
import com.intellij.python.sdk.ui.icons.PythonSdkUIIcons
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension

internal val autoSetupWithAIAction = object : AnAction(
  { PySdkUiBundle.message("evo.sdk.status.bar.popup.shortcuts.ai") },
  { "" },
  AllIcons.Toolwindows.ToolWindowAskAI,
) {
  override fun actionPerformed(e: AnActionEvent) {
  }
}

internal val defaultUvAction = object : AnAction(
  { PySdkUiBundle.message("evo.sdk.status.bar.popup.shortcuts.uv") },
  { "" },
  PythonSdkUIIcons.Tools.UV,
) {
  override fun actionPerformed(e: AnActionEvent) {
  }
}


private class AutoconfigSelectSdkProvider() : EvoSelectSdkProvider {
  override fun getTreeElement(evoModuleSdk: EvoModuleSdk) = EvoTreeLazyNodeElement(
    text = PySdkUiBundle.message("evo.sdk.status.bar.popup.shortcuts.best.options"),
    icon = AllIcons.General.Layout
  ) {
    val extensions = PyProjectSdkConfigurationExtension.EP_NAME.extensionsIfPointIsRegistered.mapNotNull {
      it.getIntention(evoModuleSdk.module)?.let { intention -> it to intention }
    }

    val section = EvoTreeSection(
      label = null,
      elements = extensions.mapIndexed { idx, (extension, intention) ->
        EvoTreeLeafElement(RunConfiguratorAction(extension, intention, idx))
      }
    )

    Result.success(listOf(section))
  }
}

private val ratingIcons = listOf(
  AllIcons.Ide.Rating,
  AllIcons.Ide.Rating4,
  AllIcons.Ide.Rating3,
  AllIcons.Ide.Rating2,
  AllIcons.Ide.Rating1,
)

private class RunConfiguratorAction(
  val extension: PyProjectSdkConfigurationExtension,
  intention: String,
  order: Int,
) : AnAction({ intention }, { intention }, ratingIcons.getOrElse(order, { AllIcons.Ide.Rating1 })) {
  init {
    //templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, evoSdk.pythonVersion?.toString() ?: "n/a")
  }

  override fun actionPerformed(e: AnActionEvent) {}
}