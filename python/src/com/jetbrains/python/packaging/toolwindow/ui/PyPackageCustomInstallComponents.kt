// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.*
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.packaging.common.PythonLocalPackageSpecification
import com.jetbrains.python.packaging.common.PythonVcsPackageSpecification
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import org.jetbrains.annotations.Nls

object PyPackageCustomInstallComponents {
  private val fromVcsText: String
    get() = message("python.toolwindow.packages.add.package.from.vcs")
  private val fromDiscText: String
    get() = message("python.toolwindow.packages.add.package.from.disc")

  fun createInstallFromLocationLink(project: Project): DropDownLink<@Nls String> {
    return DropDownLink(message("python.toolwindow.packages.add.package.action"),
                        listOf(fromVcsText, fromDiscText)) {
      val service = project.service<PyPackagingToolWindowService>()

      val specification = when (it) {
        fromDiscText -> showInstallFromDiscDialog(service)
        fromVcsText -> showInstallFromVcsDialog(service)
        else -> throw IllegalStateException("Unknown operation")
      }
      if (specification != null) {
        PyPackageCoroutine.launch(project) {
          service.installPackage(specification)
        }
      }
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

  private fun showInstallFromDiscDialog(service: PyPackagingToolWindowService): PythonLocalPackageSpecification? {
    var editable = false

    val textField = TextFieldWithBrowseButton().apply {
      addBrowseFolderListener(message("python.toolwindow.packages.add.package.path.selector"), "", service.project,
                              FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor())
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
}