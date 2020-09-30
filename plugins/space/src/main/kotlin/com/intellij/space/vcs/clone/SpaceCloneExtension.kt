package com.intellij.space.vcs.clone

import com.intellij.space.settings.SpaceSettings
import com.intellij.space.ui.cleanupUrl
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionStatusLine
import com.intellij.space.messages.SpaceBundle
import icons.SpaceIcons
import platform.common.ProductName
import javax.swing.Icon

class SpaceCloneExtension : VcsCloneDialogExtension {
  override fun createMainComponent(project: Project): VcsCloneDialogExtensionComponent {
    return SpaceCloneComponent(project)
  }

  override fun getIcon(): Icon = SpaceIcons.Main

  override fun getName(): String = ProductName

  override fun getAdditionalStatusLines(): List<VcsCloneDialogExtensionStatusLine> {
    val server = cleanupUrl(SpaceSettings.getInstance().serverSettings.server)
    val status = if (server.isBlank()) SpaceBundle.message("clone.dialog.space.description.without.server") else server
    return listOf(VcsCloneDialogExtensionStatusLine.greyText(status))
  }

  override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent {
    return SpaceCloneComponent(project)
  }
}
