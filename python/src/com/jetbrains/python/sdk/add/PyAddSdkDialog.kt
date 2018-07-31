/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.sdk.add

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.sdk.add.AddSdkDialog
import com.intellij.sdk.add.AddSdkView
import com.intellij.util.ExceptionUtil
import com.intellij.util.PlatformUtils
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.sdk.PreferredSdkComparator
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.add.PyAddSdkDialog.Companion.create
import com.jetbrains.python.sdk.detectVirtualEnvs
import com.jetbrains.python.sdk.isAssociatedWithModule
import icons.PythonIcons

/**
 * The dialog may look like the normal dialog with OK, Cancel and Help buttons
 * or the wizard dialog with Next, Previous, Finish, Cancel and Help buttons.
 *
 * Use [create] to instantiate the dialog.
 *
 * @author vlan
 */
class PyAddSdkDialog private constructor(project: Project?,
                                         private val module: Module?,
                                         private val existingSdks: List<Sdk>,
                                         private val newProjectPath: String?) : AddSdkDialog(project) {
  init {
    title = "Add Python Interpreter"
  }

  override fun createViews(): List<AddSdkView> {
    val sdks = existingSdks
      .filter { it.sdkType is PythonSdkType && !PythonSdkType.isInvalid(it) }
      .sortedWith(PreferredSdkComparator())
    val panels = arrayListOf<AddSdkView>(createVirtualEnvPanel(project, module, sdks, newProjectPath),
                                         createAnacondaPanel(project, module),
                                         PyAddSystemWideInterpreterPanel(module, existingSdks))
    val extendedPanels = PyAddSdkProvider.EP_NAME.extensions
      .mapNotNull {
        it.createView(project = project, module = module, newProjectPath = newProjectPath, existingSdks = existingSdks)
          .registerIfDisposable()
      }
    panels.addAll(extendedPanels)
    return panels
  }

  override fun handleSdkCreationException(e: Exception): Boolean {
    val cause = ExceptionUtil.findCause(e, PyExecutionException::class.java)
    if (cause != null) {
      showProcessExecutionErrorDialog(project, cause)
      return true
    }
    return false
  }

  private fun <T> T.registerIfDisposable(): T = apply { (this as? Disposable)?.let { Disposer.register(disposable, it) } }

  private fun createVirtualEnvPanel(project: Project?,
                                    module: Module?,
                                    existingSdks: List<Sdk>,
                                    newProjectPath: String?): PyAddSdkPanel {
    val newVirtualEnvPanel = when {
      allowCreatingNewEnvironments(project) -> PyAddNewVirtualEnvPanel(project, module, existingSdks, newProjectPath)
      else -> null
    }
    val existingVirtualEnvPanel = PyAddExistingVirtualEnvPanel(project, module, existingSdks, newProjectPath)
    val panels = listOf(newVirtualEnvPanel,
                        existingVirtualEnvPanel)
      .filterNotNull()
    val defaultPanel = when {
      detectVirtualEnvs(module, existingSdks).any { it.isAssociatedWithModule(module) } -> existingVirtualEnvPanel
      newVirtualEnvPanel != null -> newVirtualEnvPanel
      else -> existingVirtualEnvPanel
    }
    return PyAddSdkGroupPanel("Virtualenv environment", PythonIcons.Python.Virtualenv, panels, defaultPanel)
  }

  private fun createAnacondaPanel(project: Project?, module: Module?): PyAddSdkPanel {
    val newCondaEnvPanel = when {
      allowCreatingNewEnvironments(project) -> PyAddNewCondaEnvPanel(project, module, existingSdks, newProjectPath)
      else -> null
    }
    val panels = listOf(newCondaEnvPanel,
                        PyAddExistingCondaEnvPanel(project, module, existingSdks, newProjectPath))
      .filterNotNull()
    return PyAddSdkGroupPanel("Conda environment", PythonIcons.Python.Anaconda, panels, panels[0])
  }

  companion object {
    private fun allowCreatingNewEnvironments(project: Project?) =
      project != null || !PlatformUtils.isPyCharm() || PlatformUtils.isPyCharmEducational()

    @JvmStatic
    fun create(project: Project?, module: Module?, existingSdks: List<Sdk>, newProjectPath: String?): PyAddSdkDialog {
      return PyAddSdkDialog(project = project, module = module, existingSdks = existingSdks, newProjectPath = newProjectPath).apply {
        init()
      }
    }
  }
}