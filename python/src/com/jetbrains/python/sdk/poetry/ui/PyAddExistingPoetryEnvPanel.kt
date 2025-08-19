// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry.ui

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.getOrNull
import com.jetbrains.python.onSuccess
import com.jetbrains.python.sdk.PyDetectedSdk
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import com.jetbrains.python.sdk.add.addInterpretersAsync
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.isAssociatedWithAnotherModule
import com.jetbrains.python.sdk.poetry.*
import com.jetbrains.python.ui.pyMayBeModalBlocking
import com.jetbrains.python.util.runWithModalBlockingOrInBackground
import java.awt.BorderLayout
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon


class PyAddExistingPoetryEnvPanel(
  private val project: Project,
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
        val sdks = pyMayBeModalBlocking {
          detectPoetryEnvs(module, existingSdkPaths, module.basePath)
        }.filterNot { it.isAssociatedWithAnotherModule(module) }

        sdks.forEach { sdkToModule.putIfAbsent(it.name, module) }
        sdks.stream()
      }.toList()

      val rootSdks = pyMayBeModalBlocking {
        detectPoetryEnvs(module, existingSdkPaths, project.basePath ?: newProjectPath)
      }.filterNot { it.isAssociatedWithAnotherModule(module) }

      val moduleSdkPaths = moduleSdks.map { it.name }.toSet()
      val sdks = rootSdks.filterNot { moduleSdkPaths.contains(it.name) } + moduleSdks

      sdks.sortedBy { it.name }
    }
  }

  override fun validateAll(): List<ValidationInfo> {
    return emptyList()

  }

  override fun getOrCreateSdk(): Sdk? {
    return when (val sdk = sdkComboBox.selectedSdk) {
      is PyDetectedSdk -> {
        val mappedModule = sdkToModule[sdk.name] ?: module

        runWithModalBlockingOrInBackground(project, msg = PyBundle.message("python.sdk.dialog.title.setting.up.poetry.environment")) {
          val moduleBasePath = mappedModule?.basePath?.let { Path.of(it) }
                               ?: error("module base path is invalid: ${mappedModule?.basePath}")

          val basePythonBinaryPath = getPythonExecutable(sdk.name).let { Path.of(it) }
                                     ?: error("base python binary path is invalid, home path is ${sdk.name}")

          createPoetrySdk(moduleBasePath, existingSdks, basePythonBinaryPath).onSuccess {
            PySdkSettings.instance.preferredVirtualEnvBaseSdk = getPythonExecutable(sdk.name)
          }
        }.getOrNull()
      }
      else -> sdk
    }
  }
}
