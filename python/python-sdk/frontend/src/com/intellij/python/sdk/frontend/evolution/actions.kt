package com.intellij.python.sdk.frontend.evolution

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.python.sdk.common.evolution.EvoSdkDto
import com.intellij.python.sdk.common.evolution.getAddress
import com.intellij.python.sdk.frontend.PySdkFrontendBundle

class AddNewEnvAction : AnAction(
  { PySdkFrontendBundle.message("evolution.action.add.new.env.text") },
  { PySdkFrontendBundle.message("evolution.action.add.new.env.description") },
  AllIcons.General.InlineAdd,
) {
  override fun actionPerformed(e: AnActionEvent) = Unit
}

class SelectEnvAction(
  val evoSdk: EvoSdkDto,
) : AnAction({ evoSdk.getAddress() }, { evoSdk.getDescription() }, evoSdk.icon.icon()) {
  init {
    templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, evoSdk.pythonVersion ?: "n/a")
  }

  override fun actionPerformed(e: AnActionEvent) {}
}
