// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProjectWizard.impl

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup.CollapsedActionGroup
import com.intellij.platform.DirectoryProjectGenerator
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.newProjectWizard.impl.emptyProject.PyV3EmptyProjectGenerator
import com.jetbrains.python.newProjectWizard.PyV3BaseProjectSettings
import com.jetbrains.python.newProjectWizard.PyV3ProjectBaseGenerator
import com.jetbrains.python.newProjectWizard.promotion.PromoProjectGenerator
import com.jetbrains.python.newProjectWizard.promotion.PromoStep

internal class PyV3NewProjectStepAction : AbstractNewProjectStep<PyV3BaseProjectSettings>(PyV3Customization) {

  private object PyV3Customization : Customization<PyV3BaseProjectSettings>() {
    override fun createEmptyProjectGenerator(): DirectoryProjectGenerator<PyV3BaseProjectSettings> = PyV3EmptyProjectGenerator()

    override fun createProjectSpecificSettingsStep(
      projectGenerator: DirectoryProjectGenerator<PyV3BaseProjectSettings>,
      callback: AbstractCallback<PyV3BaseProjectSettings>,
    ): ProjectSettingsStepBase<PyV3BaseProjectSettings> =
      when (projectGenerator) {
        // Python projects with project path, python SDK and other settings
        is PyV3ProjectBaseGenerator<*> -> PyV3ProjectSpecificStep(projectGenerator, callback)
        // No "create" button, no any other setting: just promotion
        is PromoProjectGenerator -> PromoStep(projectGenerator)
        // Some other generator like node
        else -> ProjectSettingsStepBase(projectGenerator, callback)
      }


    override fun getActions(
      generators: List<DirectoryProjectGenerator<*>>,
      callback: AbstractCallback<PyV3BaseProjectSettings>,
    ): Array<out AnAction> {
      // Show non python actions as a collapsed group
      val actions = super.getActions(generators, callback)
      val (pythonActions, nonPythonActions) = actions
        .partition { it is PyV3ProjectSpecificStep || it is PromoStep && it.generator.isPython }
      return arrayOf<AnAction>(
        DefaultActionGroup(PyCharmCommunityCustomizationBundle.message("new.project.python.group.name"), pythonActions),
        CollapsedActionGroup(PyCharmCommunityCustomizationBundle.message("new.project.other.group.name"), nonPythonActions)
      )
    }
  }
}