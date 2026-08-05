// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions.jcef.commandRunner

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

@ApiStatus.Internal
fun withMarkdownCommandWorkingDirectory(
  project: Project, virtualFile: VirtualFile?,
  component: Component, x: Int, y: Int,
  action: (String) -> Unit,
) {
  val fileParent = virtualFile?.parent ?: return
  val fileDirectory = fileParent.canonicalPath ?: return
  val projectBaseDirectory = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(virtualFile)
  val projectDirectory = projectBaseDirectory?.canonicalPath ?: fileDirectory
  if (fileDirectory == projectDirectory) {
    action(fileDirectory)
    return
  }

  val settings = MarkdownSettings.getInstance(project)
  val useFileDirectoryForCommands = settings.useFileDirectoryForCommands
  if (useFileDirectoryForCommands != null) {
    action(if (useFileDirectoryForCommands) fileDirectory else projectDirectory)
    return
  }

  fun choose(useFileDirectory: Boolean) {
    settings.useFileDirectoryForCommands = useFileDirectory
    action(if (useFileDirectory) fileDirectory else projectDirectory)
  }

  val fileDirectoryRelativePath =
    projectBaseDirectory?.let { VfsUtilCore.getRelativePath(fileParent, it, '/') }?.let { "/$it" }
    ?: fileParent.name
  val choices = DefaultActionGroup(
    DumbAwareAction.create(MarkdownBundle.message("markdown.runner.directory.popup.project")) { choose(false) },
    DumbAwareAction.create(MarkdownBundle.message("markdown.runner.directory.popup.file", fileDirectoryRelativePath)) { choose(true) },
  )
  ActionManager.getInstance()
    .createActionPopupMenu(ActionPlaces.EDITOR_GUTTER, choices)
    .component
    .show(component, x, y)
}
