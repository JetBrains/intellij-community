// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.google.accounts.GoogleAccountManager
import org.intellij.plugins.markdown.google.accounts.GoogleAccountsListModel
import org.intellij.plugins.markdown.google.utils.GoogleAccountsUtils.createGoogleAccountPanel
import java.awt.Dimension
import javax.swing.JComponent

internal class GoogleChooseAccountDialog(
  project: Project,
  private val accountsListModel: GoogleAccountsListModel,
  private val accountManager: GoogleAccountManager
) : DialogWrapper(project, false) {

  init {
    this.title = MarkdownBundle.message("markdown.google.accounts.dialog.title")
    setOKButtonText(MarkdownBundle.message("markdown.google.accounts.dialog.ok.button"))

    init()
  }

  override fun createCenterPanel(): JComponent {
    return createGoogleAccountPanel(disposable, accountsListModel, accountManager).also { it.reset() }.apply {
      preferredSize = Dimension(650, 250)
    }
  }

  override fun doValidate(): ValidationInfo? {
    return if (accountsListModel.selectedAccount == null) {
      ValidationInfo(MarkdownBundle.message("markdown.google.accounts.choose.dialog.account.must.be.selected"))
    }
    else null
  }
}
