// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.conda

import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.configuration.ui.PyAddNewCondaEnvFromFilePanel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import java.awt.BorderLayout
import javax.swing.JPanel

internal class CondaCreateSdkDialog(
  module: Module,
  condaBinary: VirtualFile?,
  environmentYml: VirtualFile,
) : DialogWrapper(module.project, false, IdeModalityType.IDE) {
  private val panel = PyAddNewCondaEnvFromFilePanel(module, condaBinary?.toNioPath(), environmentYml)

  val envData
    get() = panel.envData

  init {
    title = PyBundle.message("python.sdk.creating.conda.environment.title")
    init()
    Disposer.register(disposable) { if (isOK) panel.logData() }
  }

  override fun createCenterPanel() = JPanel(BorderLayout()).apply {
    val border = IdeBorderFactory.createEmptyBorder(JBUI.insets(4, 0, 6, 0))
    val message = PyCharmCommunityCustomizationBundle.message("sdk.create.condaenv.permission")

    add(
      JBUI.Panels.simplePanel(JBLabel(message)).withBorder(border),
      BorderLayout.NORTH
    )

    add(panel, BorderLayout.CENTER)
  }

  override fun postponeValidation(): Boolean = false

  override fun doValidateAll(): List<ValidationInfo> = panel.validateAll()
}