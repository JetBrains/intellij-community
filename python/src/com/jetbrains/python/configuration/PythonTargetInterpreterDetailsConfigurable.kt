// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import javax.swing.JComponent

/**
 * The underlying configurable in [PythonInterpreterDetailsConfigurable] for target-based Python interpreters.
 *
 * Note that the provided [newSdkAdditionalData] might differ from one initially set to [sdk].
 *
 * @param newSdkAdditionalData the additional data for SDK to edit
 */
internal class PythonTargetInterpreterDetailsConfigurable(private val project: Project,
                                                          private val sdk: Sdk,
                                                          private var newSdkAdditionalData: PyTargetAwareAdditionalData,
                                                          private val targetConfigurable: Configurable)
  : NamedConfigurable<Sdk>() {
  /**
   * [Sdk.getHomePath] should not be used as [initialPythonInterpreterPath] as it might contain a credentials prefix (f.e. `docker://`),
   * when the provided `sdkAdditionalData` is converted from `PyRemoteSdkAdditionalData`
   */
  private var initialPythonInterpreterPath = newSdkAdditionalData.interpreterPath.orEmpty()

  private var pythonInterpreterPath = initialPythonInterpreterPath

  /**
   * Contains a text field for editing [pythonInterpreterPath].
   *
   * Note that [DialogPanel.apply] must be called to set [pythonInterpreterPath] according to the text field's value.
   */
  private lateinit var pythonInterpreterDetailsDialogPanel: DialogPanel

  override fun isModified(): Boolean = targetConfigurable.isModified || pythonInterpreterDetailsDialogPanel.isModified()

  override fun apply() {
    targetConfigurable.apply()
    // this updates `pythonInterpreterPath`
    pythonInterpreterDetailsDialogPanel.apply()
    // apply Python interpreter path
    val sdkModificator = sdk.sdkModificator
    sdkModificator.sdkAdditionalData = newSdkAdditionalData
    sdkModificator.updatePythonInterpreterPath(pythonInterpreterPath)
    // note that after committing the changes `sdkModificator` becomes unusable
    runWriteAction { sdkModificator.commitChanges() }

    PythonSdkUpdater.updateVersionAndPathsSynchronouslyAndScheduleRemaining(sdk, project)
  }

  override fun getDisplayName(): String = targetConfigurable.displayName

  override fun setDisplayName(name: String?) {
    // We rely on the separate "Rename" action for changing Python interpreter name
  }

  override fun getEditableObject(): Sdk = sdk

  override fun getBannerSlogan(): String = PyBundle.message("python.interpreter.banner.slogan", displayName)

  override fun createOptionsPanel(): JComponent {
    pythonInterpreterDetailsDialogPanel = panel {
      val targetConfigurableComponent = targetConfigurable.createComponent()
      if (targetConfigurableComponent != null) {
        row {
          cell(targetConfigurableComponent)
        }
      }
      else {
        // Due to the contract of `com.intellij.openapi.options.UnnamedConfigurable.createComponent()` method this could happen, but it seems
        // to be highly unlikely
        LOG.error("Target configurable of Python interpreter \"${targetConfigurable.displayName}\" returned `null` component")
      }
      row(PyBundle.message("python.interpreter.label")) {
        textField().bindText(::pythonInterpreterPath)
          .align(AlignX.FILL)
          .enabled(false)
      }
    }
    return pythonInterpreterDetailsDialogPanel
  }

  override fun disposeUIResources() {
    targetConfigurable.disposeUIResources()
  }

  companion object {
    private val LOG = logger<PythonTargetInterpreterDetailsConfigurable>()

    private fun SdkModificator.updatePythonInterpreterPath(pythonInterpreterPath: String) {
      homePath = pythonInterpreterPath
      (sdkAdditionalData as? PyTargetAwareAdditionalData)?.interpreterPath = pythonInterpreterPath
    }
  }
}