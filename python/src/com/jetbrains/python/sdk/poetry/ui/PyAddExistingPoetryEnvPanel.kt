// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry.ui

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.PyAddSdkView
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import com.jetbrains.python.sdk.add.addInterpretersAsync
import com.jetbrains.python.sdk.poetry.*
import com.jetbrains.python.ui.pyModalBlocking
import java.awt.BorderLayout
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon


class PyAddExistingPoetryEnvPanel(
  private val project: Project?,
  private val module: Module?,
  private val existingSdks: List<Sdk>,
  override var newProjectPath: String?,
) : PyAddSdkPanel() {
  private var sdkToModule = ConcurrentHashMap<String, Module>()

  @Suppress("DialogTitleCapitalization")
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
        val sdks = pyModalBlocking {
          detectPoetryEnvs(module, existingSdkPaths, module.basePath)
        }.filterNot { it.isAssociatedWithAnotherModule(module) }

        sdks.forEach { sdkToModule.putIfAbsent(it.name, module) }
        sdks.stream()
      }.toList()

      val rootSdks = pyModalBlocking {
        detectPoetryEnvs(module, existingSdkPaths, project?.basePath ?: newProjectPath)
      }.filterNot { it.isAssociatedWithAnotherModule(module) }

      val moduleSdkPaths = moduleSdks.map { it.name }.toSet()
      val sdks = rootSdks.filterNot { moduleSdkPaths.contains(it.name) } + moduleSdks

      sdks.sortedBy { it.name }
    }
  }

  override fun validateAll(): List<ValidationInfo> {
    return listOfNotNull(validateSdkComboBox(sdkComboBox, this))
  }

  override fun getOrCreateSdk(): Sdk? {
    return when (val sdk = sdkComboBox.selectedSdk) {
      is PyDetectedSdk -> {
        val mappedModule = sdkToModule[sdk.name] ?: module
        pyModalBlocking {
          setupPoetrySdkUnderProgress(project, mappedModule, existingSdks, newProjectPath,
                                      getPythonExecutable(sdk.name), false, sdk.name).onSuccess {
            PySdkSettings.instance.preferredVirtualEnvBaseSdk = getPythonExecutable(sdk.name)
          }
        }.getOrNull()
      }
      else -> sdk
    }
  }

  // FIXME: @Egor
  fun validateSdkComboBox(field: PySdkPathChoosingComboBox, view: PyAddSdkView): ValidationInfo? {
    return when (val sdk = field.selectedSdk) {
      null -> ValidationInfo(PySdkBundle.message("python.sdk.field.is.empty"), field)
      else -> null
    }
  }
}
