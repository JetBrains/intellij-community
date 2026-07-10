// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

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
internal class PythonInterpreterDetailsConfigurable(
  project: Project,
  module: Module?,
  val sdk: Sdk,
  parentConfigurable: Configurable,
) : NamedConfigurable<Sdk>() {

  private val underlyingConfigurable: Configurable = createPythonInterpreterConfigurable(project, module, sdk, parentConfigurable)

  override fun isModified(): Boolean = underlyingConfigurable.isModified

  override fun apply() {
    underlyingConfigurable.apply()
  }

  override fun getDisplayName(): @NlsSafe String = sdk.name

  // The interpreter is renamed via PythonInterpreterMasterDetails' "Rename" action (which mutates the SDK directly), not by inline
  // tree editing, so there is nothing to store here.
  override fun setDisplayName(name: String?) {}

  override fun getEditableObject(): Sdk = sdk

  override fun getBannerSlogan(): String = sdk.name

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