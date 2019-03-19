// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.conda

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.layout.*
import com.jetbrains.python.PyBundle
import com.jetbrains.python.actions.InstallCondaActionImpl
import javax.swing.JTextField

/**
 * @author Aleksey.Rostovskiy
 */
class InstallCondaActionDialog(private val project: Project?) : DialogWrapper(project) {
  private val installationPath = JTextField(InstallCondaActionImpl.defaultDirectoryFile.absolutePath)

  private val fileChooser = TextFieldWithBrowseButton(installationPath) {
    val virtualFile = LocalFileSystem.getInstance().findFileByPath(getPathInstallation())

    val path = FileChooserDialogImpl(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project)
      .choose(project, virtualFile)
      .firstOrNull()
      ?.path
    installationPath.text = path ?: InstallCondaActionImpl.defaultDirectoryFile.absolutePath
  }

  init {
    title = PyBundle.message("action.SetupMiniconda.actionName")
    init()
  }

  override fun doOKAction() {
    val errorMessage = InstallCondaActionImpl.checkPath(getPathInstallation())

    if (errorMessage != null) {
      Messages.showErrorDialog(project,
                               errorMessage,
                               PyBundle.message("action.SetupMiniconda.installFailed"))
    }
    else {
      super.doOKAction()
    }
  }

  override fun createCenterPanel() =
    panel {
      row {
        label(PyBundle.message("action.SetupMiniconda.specifyPath"))
      }
      row {
        fileChooser()
      }
    }

  fun getPathInstallation(): String = installationPath.text
}