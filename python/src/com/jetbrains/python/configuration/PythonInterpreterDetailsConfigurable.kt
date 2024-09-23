// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Delegates configuration to the underlying configurable that depends on Python interpreter type.
 */
class PythonInterpreterDetailsConfigurable(project: Project,
                                           module: Module?,
                                           val sdk: Sdk,
                                           parentConfigurable: Configurable) : NamedConfigurable<Sdk>() {

  private val underlyingConfigurable: Configurable = createPythonInterpreterConfigurable(project, module, sdk, parentConfigurable)
  private var initialSdkName: String = sdk.name
  private var currentSdkName: @NlsSafe String = sdk.name

  override fun isModified(): Boolean = initialSdkName != currentSdkName || underlyingConfigurable.isModified

  override fun apply() {
    underlyingConfigurable.apply()

    if (currentSdkName != initialSdkName) {
      WriteAction.run<Throwable> {
        val sdkModificator = sdk.sdkModificator
        sdkModificator.name = currentSdkName
        sdkModificator.commitChanges()
        initialSdkName = currentSdkName
      }
    }
  }

  override fun getDisplayName(): @NlsSafe String {
    return currentSdkName
  }

  override fun setDisplayName(name: String?) {
    currentSdkName = name.orEmpty()
  }

  override fun getEditableObject(): Sdk = sdk

  override fun getBannerSlogan(): String = currentSdkName

  override fun createOptionsPanel(): JComponent = underlyingConfigurable.createComponent()?.apply { setDefaultBorder() } ?: JPanel()

  override fun disposeUIResources() {
    underlyingConfigurable.disposeUIResources()
  }

  companion object {

    private fun JComponent.setDefaultBorder() {
      border = JBUI.Borders.empty(11, 16, 16, 16)
    }
  }
}