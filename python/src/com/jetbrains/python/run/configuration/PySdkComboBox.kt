// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Computable
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams
import com.jetbrains.python.sdk.PySdkListCellRenderer
import com.jetbrains.python.sdk.PythonSdkUtil
import java.util.function.Consumer

class PySdkComboBox(private val addDefault: Boolean,
                    private val moduleProvider: Computable<out Module?>) : ComboBox<Sdk?>(), PyInterpreterModeNotifier {
  private val interpreterModeListeners: MutableList<Consumer<Boolean>> = mutableListOf()

  fun reset(config: AbstractPythonRunConfigurationParams) {
    initList()
    selectedItem = config.sdk
    setUseModuleSdk(config.isUseModuleSdk)
    setModule(config.module)
    addActionListener {
      updateRemoteInterpreterMode()
    }
    updateRemoteInterpreterMode()
  }

  fun initList() {
    val pythonSdks: MutableList<Sdk?> = PythonSdkUtil.getAllSdks().toMutableList()
    if (addDefault) {
      pythonSdks.add(0, null)
    }
    for (item in pythonSdks) {
      addItem(item)
    }
  }

  fun apply(config: AbstractPythonRunConfigurationParams) {
    config.sdk = selectedItem as? Sdk
    config.isUseModuleSdk = isUseModuleSdk()
  }

  fun setModule(module: Module?) {
    updateDefaultInterpreter(module)
    updateRemoteInterpreterMode()
  }

  private fun updateDefaultInterpreter(module: Module?) {
    val sdk = if (module == null) null else ModuleRootManager.getInstance(module).sdk
    setRenderer(
      if (sdk == null) PySdkListCellRenderer()
      else PySdkListCellRenderer(PyBundle.message("python.sdk.rendering.project.default.0", sdk.name), sdk)
    )
  }

  private fun setUseModuleSdk(useModuleSdk: Boolean) {
    if (selectedItem != null && useModuleSdk) {
      selectedItem = null
    }
  }

  private fun isUseModuleSdk(): Boolean = addDefault && selectedItem == null

  fun getSelectedSdk(): Sdk? {
    val selectedSdk = selectedItem as? Sdk
    if (selectedSdk != null) {
      return selectedSdk
    }
    else {
      if (isUseModuleSdk()) {
        moduleProvider.get()?.let {
          return@getSelectedSdk PythonSdkUtil.findPythonSdk(it)
        }
      }
    }
    return null
  }

  override fun isRemoteSelected(): Boolean = PythonSdkUtil.isRemote(getSelectedSdk())

  private fun updateRemoteInterpreterMode() {
    val isRemote = isRemoteSelected()
    for (listener in interpreterModeListeners) {
      listener.accept(isRemote)
    }
  }

  override fun addInterpreterModeListener(listener: Consumer<Boolean>) {
    interpreterModeListeners.add(listener)
  }
}