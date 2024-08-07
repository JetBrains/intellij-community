// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add.target

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.jetbrains.python.PyBundle
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import java.util.function.Consumer
import javax.swing.JComponent

/**
 * Use [PyAddTargetBasedSdkDialog.Companion.show] to instantiate and show the dialog.
 */
class PyAddTargetBasedSdkDialog private constructor(private val project: Project?,
                                                    private val module: Module?,
                                                    private val existingSdks: List<Sdk>,
                                                    private val targetEnvironmentConfiguration: TargetEnvironmentConfiguration?)
  : DialogWrapper(project) {
  private val centerPanel: PyAddTargetBasedSdkPanel

  init {
    title = PyBundle.message("python.sdk.add.python.interpreter.title")

    centerPanel = PyAddTargetBasedSdkPanel(project, module, existingSdks, targetEnvironmentConfiguration?.let { { it } },
                                           config = PythonLanguageRuntimeConfiguration(),
                                           introspectable = null).apply {
      Disposer.register(disposable, this)
    }
  }

  override fun createCenterPanel(): JComponent = centerPanel.createCenterPanel()

  override fun postponeValidation(): Boolean = false

  override fun doValidateAll(): List<ValidationInfo> = centerPanel.doValidateAll()

  fun getOrCreateSdk(): Sdk? = centerPanel.getOrCreateSdk()

  /**
   * Tries to create the SDK and closes the dialog if the creation succeeded.
   *
   * @see [doOKAction]
   */
  override fun doOKAction() {
    centerPanel.doOKAction()

    close(OK_EXIT_CODE)
  }

  companion object {
    /**
     * The case when [targetEnvironmentConfiguration] is `null` means the local
     * target.
     */
    @JvmStatic
    fun show(project: Project?,
             module: Module?,
             existingSdks: List<Sdk>,
             sdkAddedCallback: Consumer<Sdk?>,
             targetEnvironmentConfiguration: TargetEnvironmentConfiguration? = null) {
      val dialog = PyAddTargetBasedSdkDialog(project = project,
                                             module = module,
                                             existingSdks = existingSdks,
                                             targetEnvironmentConfiguration = targetEnvironmentConfiguration)
      dialog.init()

      val sdk = if (dialog.showAndGet()) dialog.getOrCreateSdk() else null
      sdkAddedCallback.accept(sdk)
    }
  }
}

