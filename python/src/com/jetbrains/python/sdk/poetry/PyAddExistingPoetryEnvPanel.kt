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
@file:Suppress("DialogTitleCapitalization")

package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import com.jetbrains.python.sdk.add.PyAddSdkView
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import com.jetbrains.python.sdk.add.addInterpretersAsync
import java.awt.BorderLayout
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import kotlin.streams.toList

/**
 * @author vlan
 */

/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

class PyAddExistingPoetryEnvPanel(private val project: Project?,
                                  private val module: Module?,
                                  private val existingSdks: List<Sdk>,
                                  override var newProjectPath: String?,
                                  context: UserDataHolder) : PyAddSdkPanel() {
  private var sdkToModule = ConcurrentHashMap<String, Module>()
  override val panelName: String get() = PyBundle.message("python.add.sdk.panel.name.existing.environment")
  override val icon: Icon = POETRY_ICON
  private val sdkComboBox = PySdkPathChoosingComboBox()

  init {
    layout = BorderLayout()
    val formPanel = FormBuilder.createFormBuilder()
      .addLabeledComponent(PySdkBundle.message("python.interpreter.label"), sdkComboBox)
      .panel
    add(formPanel, BorderLayout.NORTH)
    addInterpretersAsync(sdkComboBox) {
      val existingSdkPaths = sdkHomes(existingSdks)
      val moduleSdks = allModules(project).parallelStream().flatMap { module ->
        val sdks = detectPoetryEnvs(module, existingSdkPaths, module.basePath)
          .filterNot { it.isAssociatedWithAnotherModule(module) }
        sdks.forEach { sdkToModule.putIfAbsent(it.name, module) }
        sdks.stream()
      }.toList()
      val rootSdks = detectPoetryEnvs(module, existingSdkPaths, project?.basePath ?: newProjectPath)
        .filterNot { it.isAssociatedWithAnotherModule(module) }
      val moduleSdkPaths = moduleSdks.map { it.name }.toSet()
      val sdks = rootSdks.filterNot { moduleSdkPaths.contains(it.name) } + moduleSdks
      sdks.sortedBy { it.name }
    }
  }

  override fun validateAll(): List<ValidationInfo> = listOfNotNull(validateSdkComboBox(sdkComboBox, this))

  override fun getOrCreateSdk(): Sdk? {
    return when (val sdk = sdkComboBox.selectedSdk) {
      is PyDetectedSdk -> {
        val mappedModule = sdkToModule[sdk.name] ?: module
        setupPoetrySdkUnderProgress(project, mappedModule, existingSdks, newProjectPath,
          getPythonExecutable(sdk.name), false, sdk.name)?.apply {
          PySdkSettings.instance.preferredVirtualEnvBaseSdk = getPythonExecutable(sdk.name)
        }
      }
      else -> sdk
    }
  }

  companion object {
    fun validateSdkComboBox(field: PySdkPathChoosingComboBox, view: PyAddSdkView): ValidationInfo? {
      return when (val sdk = field.selectedSdk) {
        null -> ValidationInfo(PySdkBundle.message("python.sdk.field.is.empty"), field)
        // This plugin does not support installing python sdk.
        //                is PySdkToInstall -> {
        //                    val message = sdk.getInstallationWarning(getDefaultButtonName(view))
        //                    ValidationInfo(message).asWarning().withOKEnabled()
        //                }
        else -> null
      }
    }
  }
}
