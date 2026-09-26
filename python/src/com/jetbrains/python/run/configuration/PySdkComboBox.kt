// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Computable
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.python.sdk.backend.asItem
import com.intellij.python.sdk.backend.findSdk
import com.intellij.python.sdk.backend.pyInterpreterItems
import com.intellij.python.sdk.backend.pythonInterpreterAsync
import com.intellij.python.sdk.common.PyInterpreterItem
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams
import com.jetbrains.python.sdk.PySdkListCellRenderer
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pythonSdk
import java.util.function.Consumer

/**
 * The interpreter combo of a run configuration.
 *
 * It holds [PyInterpreterItem]s rather than SDKs: a row states whether its interpreter can be used, and only the
 * interpreter can answer that. The items are read under a progress, off the EDT.
 */
class PySdkComboBox(private val addDefault: Boolean,
                    private val moduleProvider: Computable<out Module?>) : ComboBox<PyInterpreterItem?>(), PyInterpreterModeNotifier {
  private val interpreterModeListeners: MutableList<Consumer<Boolean>> = mutableListOf()

  fun reset(config: AbstractPythonRunConfigurationParams) {
    initList()
    selectedItem = config.sdk?.let { itemFor(it) }
    setUseModuleSdk(config.isUseModuleSdk)
    setModule(config.module)
    addActionListener {
      updateRemoteInterpreterMode()
    }
    updateRemoteInterpreterMode()
  }

  fun initList() {
    val items: MutableList<PyInterpreterItem?> = readInterpreters { PythonSdkUtil.getAllSdks().pyInterpreterItems() }.toMutableList()
    if (addDefault) {
      items.add(0, null)
    }
    removeAllItems() // initList is called at least twice: on creation and on reset, so we need to clean it up 
    for (item in items) {
      addItem(item)
    }
  }

  fun apply(config: AbstractPythonRunConfigurationParams) {
    config.sdk = getSelectedSdk()
    config.isUseModuleSdk = isUseModuleSdk()
  }

  fun setModule(module: Module?) {
    updateDefaultInterpreter(module)
    updateRemoteInterpreterMode()
  }

  private fun updateDefaultInterpreter(module: Module?) {
    val sdk = module?.pythonSdk
    setRenderer(
      if (sdk == null) PySdkListCellRenderer()
      else PySdkListCellRenderer(PyBundle.message("python.sdk.rendering.project.default.0", sdk.name), itemFor(sdk))
    )
  }

  private fun setUseModuleSdk(useModuleSdk: Boolean) {
    if (selectedItem != null && useModuleSdk) {
      selectedItem = null
    }
  }

  private fun isUseModuleSdk(): Boolean = addDefault && selectedItem == null

  fun getSelectedSdk(): Sdk? {
    val selectedSdk = (selectedItem as? PyInterpreterItem)?.findSdk()
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

  private fun itemFor(sdk: Sdk): PyInterpreterItem = readInterpreters { listOf(sdk.pythonInterpreterAsync().asItem()) }.single()

  private fun <T> readInterpreters(read: suspend () -> T): T =
    runWithModalProgressBlocking(ModalTaskOwner.component(this), PyBundle.message("python.interpreters.reading.interpreters.progress")) {
      read()
    }
}
