// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.options.MasterDetails
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.newEditor.SettingsDialogFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DetailsComponent
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.ModuleOrProject.*
import javax.swing.JComponent

class PythonInterpreterConfigurable(moduleOrProject: ModuleOrProject) : SearchableConfigurable, MasterDetails {
  private val masterDetailsComponent: PythonInterpreterMasterDetails = PythonInterpreterMasterDetails(moduleOrProject, this)

  override fun createComponent(): JComponent {
    val component = masterDetailsComponent.createComponent()
    val panelInsets = UIUtil.getRegularPanelInsets()
    // reset the bottom inset to zero as the tree does not have a proper line border around it and with the inset it "flows in the air"
    panelInsets.bottom = 0
    // the right inset is already added by the detail configurable
    panelInsets.right = 0
    UIUtil.addInsets(component, panelInsets)
    return component
  }

  override fun isModified(): Boolean = masterDetailsComponent.isModified

  override fun apply() = masterDetailsComponent.apply()

  override fun reset() = masterDetailsComponent.reset()

  override fun disposeUIResources() = masterDetailsComponent.disposeUIResources()

  override fun getDisplayName(): String = masterDetailsComponent.displayName

  override fun getId(): String = "Python.Interpreter.List.V2"

  override fun initUi() = masterDetailsComponent.initUi()

  override fun getToolbar(): JComponent = masterDetailsComponent.toolbar

  override fun getMaster(): JComponent = masterDetailsComponent.master

  override fun getDetails(): DetailsComponent = masterDetailsComponent.details

  companion object {
    private val DIMENSION_KEY = PythonInterpreterConfigurable::class.simpleName + ".size"

    @JvmStatic
    fun openInDialog(project: Project, module: Module?, initiallySelectedSdk: Sdk?): Sdk? {
      val configurable = PythonInterpreterConfigurable(if (module != null) ModuleAndProject(module) else ProjectOnly(project))
      // `ShowSettingsUtil.editConfigurable()` with `advancedInitialization` parameter could be possibly also used here
      val dialogWrapper = SettingsDialogFactory.getInstance().create(project, DIMENSION_KEY, configurable, true, false)
      // select project Python interpreter
      configurable.masterDetailsComponent.selectNodeInTree(initiallySelectedSdk)
      val isOKClicked = dialogWrapper.showAndGet()
      // note that clicking "Apply" button and then "Cancel" results in `false` value of `isOKClicked`
      return if (isOKClicked) {
        configurable.masterDetailsComponent.storedSelectedSdk
      }
      else {
        configurable.masterDetailsComponent.projectSdksModel.reset(project)
        null
      }
    }
  }
}