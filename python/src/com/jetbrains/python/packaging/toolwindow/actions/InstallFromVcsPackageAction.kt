// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.*
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.common.PythonVcsPackageSpecification
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.utils.PyPackageCoroutine

internal class InstallFromVcsPackageAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val service = PyPackagingToolWindowService.getInstance(project)
    val specification = showInstallFromVcsDialog(service) ?: return
    PyPackageCoroutine.launch(project) {
      service.installPackage(specification)
    }
  }

  private fun showInstallFromVcsDialog(service: PyPackagingToolWindowService): PythonVcsPackageSpecification? {
    var editable = false
    var link = ""
    val systems = listOf(message("python.toolwindow.packages.add.package.vcs.git"),
                         message("python.toolwindow.packages.add.package.vcs.svn"),
                         message("python.toolwindow.packages.add.package.vcs.hg"),
                         message("python.toolwindow.packages.add.package.vcs.bzr"))
    var vcs = systems.first()

    val panel = panel {
      row {
        comboBox(systems)
          .bindItem({ vcs }, { vcs = it!! })
        textField()
          .columns(COLUMNS_MEDIUM)
          .bindText({ link }, { link = it })
          .align(AlignX.FILL)
      }
      row {
        checkBox(message("python.toolwindow.packages.add.package.as.editable"))
          .bindSelected({ editable }, { editable = it })
      }
    }

    val shouldInstall = dialog(message("python.toolwindow.packages.add.package.dialog.title"), panel, project = service.project, resizable = true).showAndGet()
    if (shouldInstall) {
      val prefix = when (vcs) {
        message("python.toolwindow.packages.add.package.vcs.git") -> "git+"
        message("python.toolwindow.packages.add.package.vcs.svn") -> "svn+"
        message("python.toolwindow.packages.add.package.vcs.hg") -> "hg+"
        message("python.toolwindow.packages.add.package.vcs.bzr") -> "bzr+"
        else -> throw IllegalStateException("Unknown VCS")
      }

      return PythonVcsPackageSpecification(link, link, prefix, editable)
    }
    return null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}