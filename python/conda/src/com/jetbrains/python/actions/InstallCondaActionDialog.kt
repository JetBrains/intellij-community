// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.layout.*
import com.jetbrains.python.conda.InstallCondaUtils
import javax.swing.JTextField

/**
 * @author Aleksey.Rostovskiy
 */
class InstallCondaActionDialog(private val project: Project?) : DialogWrapper(project) {
  private val installationPath = JTextField(InstallCondaUtils.defaultDirectoryFile.absolutePath)

  private val fileChooser = TextFieldWithBrowseButton(installationPath) {
    val virtualFile = LocalFileSystem.getInstance().findFileByPath(getPathInstallation())

    val path = FileChooserDialogImpl(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project)
      .choose(project, virtualFile)
      .firstOrNull()
      ?.path
    installationPath.text = path ?: InstallCondaUtils.defaultDirectoryFile.absolutePath
  }

  init {
    title = ActionsBundle.message("action.SetupMiniconda.actionName")
    init()
  }

  override fun doOKAction() {
    val errorMessage = InstallCondaUtils.checkPath(getPathInstallation())

    if (errorMessage != null) {
      Messages.showErrorDialog(project,
                               errorMessage,
                               ActionsBundle.message("action.SetupMiniconda.installFailed"))
    }
    else {
      super.doOKAction()
    }
  }

  override fun createCenterPanel() =
    panel {
      row {
        label(ActionsBundle.message("action.SetupMiniconda.specifyPath"))
      }
      row {
        fileChooser()
      }
    }

  fun getPathInstallation(): String = installationPath.text
}