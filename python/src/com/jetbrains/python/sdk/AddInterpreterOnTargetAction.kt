// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.execution.target.TargetCustomToolWizardStep
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.TargetEnvironmentWizard
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.target.PythonLanguageRuntimeType

internal class AddInterpreterOnTargetAction(private val project: Project,
                                            private val targetType: TargetEnvironmentType<*>,
                                            private val onSdkCreated: (sdk: Sdk) -> Unit
) : AnAction({ "On ${targetType.displayName}..." }, targetType.icon) {
  override fun actionPerformed(e: AnActionEvent) {
    val wizard = TargetEnvironmentWizard.createWizard(project, targetType, PythonLanguageRuntimeType.getInstance())
    if (wizard != null && wizard.showAndGet()) {
      val model = PyConfigurableInterpreterList.getInstance(project).model
      val sdk = (wizard.currentStepObject as? TargetCustomToolWizardStep)?.customTool as? Sdk
      if (sdk != null && model.findSdk(sdk.name) == null) {
        model.addSdk(sdk)


        model.apply()
        onSdkCreated(sdk)
      }
    }
  }
}