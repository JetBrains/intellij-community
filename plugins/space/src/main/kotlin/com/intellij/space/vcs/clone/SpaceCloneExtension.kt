// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.clone

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionStatusLine
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.settings.SpaceSettings
import com.intellij.space.ui.cleanupUrl
import icons.SpaceIcons
import javax.swing.Icon

class SpaceCloneExtension : VcsCloneDialogExtension {
  override fun createMainComponent(project: Project): VcsCloneDialogExtensionComponent {
    return SpaceCloneComponent(project)
  }

  override fun getIcon(): Icon = SpaceIcons.Main

  override fun getName(): String = SpaceBundle.message("product.name")

  override fun getAdditionalStatusLines(): List<VcsCloneDialogExtensionStatusLine> {
    val server = cleanupUrl(SpaceSettings.getInstance().serverSettings.server)
    val status = if (server.isBlank()) SpaceBundle.message("clone.dialog.space.description.without.server") else server
    return listOf(VcsCloneDialogExtensionStatusLine.greyText(status))
  }

  override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent {
    return SpaceCloneComponent(project)
  }
}
