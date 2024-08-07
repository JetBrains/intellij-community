// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.ui.DialogPanel
import com.intellij.util.concurrency.annotations.RequiresEdt

interface PyIntegratedToolsTestPanelCustomizer {
  companion object {
    private val EP_NAME: ExtensionPointName<PyIntegratedToolsTestPanelCustomizer> =
      ExtensionPointName.create("com.jetbrains.python.configuration.pyIntegratedToolsTestPanelCustomizer")

    @RequiresEdt
    @JvmStatic
    fun createPanels(): List<DialogPanel> = EP_NAME.extensionList.map { it.createPanel() }
  }

  @RequiresEdt
  fun createPanel(): DialogPanel
}