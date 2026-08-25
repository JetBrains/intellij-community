// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions.jcef.commandRunner

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

/**
 * The directory a Markdown command runs in, as a path.
 *
 * [VirtualFile.getParent] is null for the previewed file on the JetBrains Client, so the parent cannot be the only
 * source: without this the run icon is drawn and the click is then dropped (IJPL-250078). The path is the host's
 * either way, which is the machine the command runs on.
 */
@ApiStatus.Internal
fun markdownCommandFileDirectory(virtualFile: VirtualFile?): String? {
  if (virtualFile == null) return null
  virtualFile.parent?.canonicalPath?.let { return it }
  virtualFile.parent?.path?.let { return it }
  return virtualFile.path.substringBeforeLast('/', missingDelimiterValue = "").ifEmpty { null }
}

@ApiStatus.Internal
fun withMarkdownCommandWorkingDirectory(
  project: Project, virtualFile: VirtualFile?,
  component: Component, x: Int, y: Int,
  action: (String) -> Unit,
) {
  if (virtualFile == null) {
    LOG.warn("A Markdown command is not run: the preview has no file.")
    return
  }
  val fileDirectory = markdownCommandFileDirectory(virtualFile)
  if (fileDirectory == null) {
    LOG.warn("A Markdown command is not run: no directory for '${virtualFile.url}'.")
    return
  }
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
    virtualFile.parent?.let { parent -> projectBaseDirectory?.let { VfsUtilCore.getRelativePath(parent, it, '/') } }?.let { "/$it" }
    ?: fileDirectory.substringAfterLast('/')
  val choices = DefaultActionGroup(
    DumbAwareAction.create(MarkdownBundle.message("markdown.runner.directory.popup.project")) { choose(false) },
    DumbAwareAction.create(MarkdownBundle.message("markdown.runner.directory.popup.file", fileDirectoryRelativePath)) { choose(true) },
  )
  ActionManager.getInstance()
    .createActionPopupMenu(ActionPlaces.EDITOR_GUTTER, choices)
    .component
    .show(component, x, y)
}

private val LOG = fileLogger()
