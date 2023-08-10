// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.ui.VcsCloneComponent
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import org.jetbrains.idea.svn.SvnBundle
import javax.swing.JComponent

class SvnCloneDialogExtension(project: Project) : CheckoutDialog(project, ProjectLevelVcsManager.getInstance(project).compositeCheckoutListener), VcsCloneComponent {
  private val centerPanel = (createCenterPanel() as JComponent).apply {
    val insets = UIUtil.PANEL_REGULAR_INSETS
    border = JBEmptyBorder(0, insets.left, insets.bottom, insets.right)
  }

  override fun getView(): JComponent = centerPanel

  override fun doClone(listener: CheckoutProvider.Listener) = this.doOKAction()

  override fun isOkEnabled(): Boolean {
    return this.isOKActionEnabled
  }

  override fun getOkButtonText() = SvnBundle.message("checkout.dialog.button")

  override fun doValidateAll(): MutableList<ValidationInfo> {
    return super.doValidateAll()
  }

  override fun dispose() {
    super.dispose()
  }
}