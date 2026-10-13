// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.commandRunner

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.PreviewCommandRunnability
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.RunnerPlace
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.getMarkdownCommandWorkingDirectories

internal class LocalPreviewCommandRunnability : PreviewCommandRunnability {
  override fun isRunnable(project: Project, file: VirtualFile?, command: String, allowRunConfigurations: Boolean): Boolean {
    val workingDirectories = getMarkdownCommandWorkingDirectories(project, file)
    return CommandRunnerExtension.matches(project, workingDirectories, true, command, allowRunConfigurations)
  }

  override fun execute(
    project: Project,
    file: VirtualFile?,
    command: String,
    executorId: String,
    workingDirectory: String,
  ) {
    CommandRunnerExtension.executeByExecutorId(project, workingDirectory, true, command, executorId, RunnerPlace.PREVIEW)
  }

  override suspend fun resolvePending(): Boolean = false
}
