// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.*
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.common.PythonLocalPackageSpecification
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.utils.PyPackageCoroutine

internal class InstallFromDiskPackageAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val service = PyPackagingToolWindowService.getInstance(project)
    val specification = showInstallFromDiscDialog(project) ?: return
    PyPackageCoroutine.launch(project) {
      service.installPackage(specification)
    }
  }

  private fun showInstallFromDiscDialog(project: Project): PythonLocalPackageSpecification? {
    val service = PyPackagingToolWindowService.getInstance(project)
    var editable = false

    val textField = TextFieldWithBrowseButton().apply {
      addBrowseFolderListener(service.project, FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor()
        .withTitle(message("python.toolwindow.packages.add.package.path.selector")))
    }
    val panel = panel {
      row(message("python.toolwindow.packages.add.package.path")) {
        cell(textField)
          .columns(COLUMNS_MEDIUM)
          .align(AlignX.FILL)
      }
      row {
        checkBox(message("python.toolwindow.packages.add.package.as.editable"))
          .bindSelected({ editable }, { editable = it })
      }
    }

    val shouldInstall = dialog(message("python.toolwindow.packages.add.package.dialog.title"), panel, project = service.project, resizable = true).showAndGet()
    return if (shouldInstall) PythonLocalPackageSpecification(textField.text, textField.text, editable) else null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
