// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor

/**
 * Configurable for local Python interpreter.
 *
 * Replaces [EditSdkDialog].
 */
class PythonLocalInterpreterConfigurable(private val project: Project, private val module: Module?, private val sdk: Sdk)
  : BoundConfigurable(sdk.name) {
  private val initialSdkHomePath = sdk.homePath

  private val sdkModificator: SdkModificator = sdk.sdkModificator

  private var interpreterPath: String
    get() = sdkModificator.homePath
    set(value) {
      sdkModificator.homePath = value
    }

  private var isSdkAssociatedWithOtherPathInitiallyAndReset: Boolean = false

  private val wasSdkAssociatedWithPathInitially = sdk.associatedModulePath?.isNotBlank() == true

  private var isSdkAssociatedWithPath: Boolean = wasSdkAssociatedWithPathInitially

  override fun createPanel(): DialogPanel = panel {
    row(PyBundle.message("form.edit.sdk.interpreter.path")) {
      textFieldWithBrowseButton(PyBundle.message("sdk.edit.dialog.specify.interpreter.path"),
                                project,
                                PythonSdkType.getInstance().homeChooserDescriptor) { it.name }
        .bindText(::interpreterPath)
        .align(AlignX.FILL)
    }
    val sdkFlavor = PythonSdkFlavor.getPlatformIndependentFlavor(sdk.homePath)
    if (sdkFlavor is VirtualEnvSdkFlavor || sdkFlavor is CondaEnvSdkFlavor) {
      // Add SDK association components only for Virtualenv and Conda interpreters
      val sdkAssociatedModulePath = sdk.associatedModulePath
      val projectBasePath = project.basePath?.let { FileUtil.toSystemIndependentName(it) }
      if (sdkAssociatedModulePath != null && sdkAssociatedModulePath != projectBasePath) {
        lateinit var associateSdkWithProjectCheckBox: Cell<JBCheckBox>
        row {
          associateSdkWithProjectCheckBox = checkBox(PyBundle.message("sdk.edit.dialog.associate.virtual.env.with.path",
                                                                      FileUtil.toSystemDependentName(sdkAssociatedModulePath)))
            .bindSelected(::isSdkAssociatedWithPath)
            .onIsModified { isSdkAssociatedWithOtherPathInitiallyAndReset }
            .enabled(false)
        }
        // "Remove association" link is only visible when this Python interpreter is associated with **other** path than this project's base
        row {
          link(PyBundle.message("python.interpreter.local.configurable.remove.association")) {
            // update property value
            isSdkAssociatedWithPath = false
            isSdkAssociatedWithOtherPathInitiallyAndReset = true
            // hide the link **permanently**
            visible(false)
            // update "Associate ..." checkbox text and enable it
            associateSdkWithProjectCheckBox.component.text =
              PyBundle.message("form.edit.sdk.associate.this.virtual.environment.with.current.project")
            associateSdkWithProjectCheckBox.component.isSelected = false
            associateSdkWithProjectCheckBox.enabled(true)
          }
        }
      }
      else {
        row {
          checkBox(PyBundle.message("form.edit.sdk.associate.this.virtual.environment.with.current.project"))
            .bindSelected(::isSdkAssociatedWithPath)
        }
      }
    }
  }

  override fun apply() {
    super.apply()

    if (isSdkAssociatedWithOtherPathInitiallyAndReset || wasSdkAssociatedWithPathInitially != isSdkAssociatedWithPath) {
      if (isSdkAssociatedWithPath) {
        if (module != null) sdkModificator.associateWithModule(module)
        else sdkModificator.associateWithProject(project)
      }
      else {
        sdkModificator.resetAssociatedModulePath()
      }
    }

    sdkModificator.commitChanges()

    if (initialSdkHomePath != sdk.homePath) {
      PythonSdkUpdater.updateVersionAndPathsSynchronouslyAndScheduleRemaining(sdk, project)
    }
  }
}