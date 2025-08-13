package com.intellij.python.sdk.ui.evolution

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.python.sdk.ui.evolution.sdk.EvoSdk
import com.intellij.python.sdk.ui.evolution.ui.getDescription

class AddNewEnvAction : AnAction(
  { "Add New" },
  { "Description" },
  AllIcons.General.InlineAdd,
) {
  override fun actionPerformed(e: AnActionEvent) = Unit
}

class SelectEnvAction(
  val evoSdk: EvoSdk
) : AnAction({ evoSdk.getAddress() }, { evoSdk.getDescription() }, evoSdk.icon) {
  init {
    templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, evoSdk.pythonVersion?.toString() ?: "n/a")
  }

  override fun actionPerformed(e: AnActionEvent) {}
}
