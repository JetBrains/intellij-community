// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.*
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.configuration.PyProjectVirtualEnvConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Paths
import kotlin.io.path.exists

class PythonNewVirtualenvCreator(state: PythonAddInterpreterState) : PythonAddEnvironment(state) {
  private val location = propertyGraph.property("")
  private val inheritSitePackages = propertyGraph.property(false)
  private val makeAvailable = propertyGraph.property(false)
  private lateinit var versionComboBox: ComboBox<String>
  private var locationModified = false

  override fun buildOptions(panel: Panel) {
    with(panel) {
      row(message("sdk.create.custom.base.python")) {
        versionComboBox = pythonBaseInterpreterComboBox(state.basePythonHomePaths,
                                                        state.basePythonHomePath)

      }
      row(message("sdk.create.custom.location")) {
        textFieldWithBrowseButton(message("sdk.create.custom.venv.location.browse.title"), fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor())
          .bindText(location)
          .whenTextChangedFromUi { locationModified = true }
          .cellValidation {
            addInputRule("This directory already exists") {
              Paths.get(it.text).exists() // todo validation with custom component, once available
            }
          }
          .align(Align.FILL)
      }

      row("") {
        checkBox(message("sdk.create.custom.inherit.packages"))
          .bindSelected(inheritSitePackages)
      }
      row("") {
        checkBox(message("sdk.create.custom.make.available"))
          .bindSelected(makeAvailable)
      }
    }

    state.projectPath.afterChange {
      if (!locationModified) {
        location.set(PySdkSettings.instance.getPreferredVirtualEnvBasePath(it))
      }
    }
  }

  override fun onShown() {
    val modalityState = ModalityState.current().asContextElement()
    state.scope.launch(Dispatchers.Default + modalityState) {
      val basePath = PySdkSettings.instance.getPreferredVirtualEnvBasePath(state.projectPath.get())
      withContext(Dispatchers.Main + modalityState) {
        location.set(basePath)
      }
    }
  }

  override fun getOrCreateSdk(): Sdk {
    return PyProjectVirtualEnvConfiguration.createVirtualEnvSynchronously(state.basePythonVersion.get(),
                                                                          state.basePythonSdks.get(),
                                                                          location.get(),
                                                                          state.projectPath.get(),
                                                                          null, null,
                                                                          inheritSitePackages = inheritSitePackages.get(),
                                                                          makeShared = makeAvailable.get())!!
  }
}