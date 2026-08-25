// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.services

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.RunnerPlace
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.getMarkdownCommandWorkingDirectories
import org.intellij.plugins.markdown.service.MarkdownCommandRunnerRemoteApi

internal class MarkdownCommandRunnerRemoteApiImpl : MarkdownCommandRunnerRemoteApi {
  override suspend fun filterRunnable(
    projectId: ProjectId,
    virtualFileId: VirtualFileId?,
    commands: List<String>,
    allowRunConfigurations: Boolean,
  ): Set<String> {
    val project = projectId.findProjectOrNull() ?: return emptySet()
    val workingDirectories = getMarkdownCommandWorkingDirectories(project, virtualFileId?.virtualFile())
    return commands.filterTo(LinkedHashSet()) { command ->
      CommandRunnerExtension.matches(project, workingDirectories, true, command, allowRunConfigurations)
    }
  }

  override suspend fun execute(
    projectId: ProjectId,
    virtualFileId: VirtualFileId?,
    command: String,
    executorId: String,
    workingDirectory: String,
  ): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    if (!TrustedProjects.isProjectTrusted(project)) {
      LOG.info("Markdown command is not executed: the project is not trusted on the backend.")
      return false
    }
    return CommandRunnerExtension.executeByExecutorId(project, workingDirectory, true, command, executorId, RunnerPlace.PREVIEW)
  }

  override suspend fun runBlock(
    projectId: ProjectId,
    command: String,
    executorId: String,
    workingDirectory: String,
  ): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    if (!TrustedProjects.isProjectTrusted(project)) {
      LOG.info("Markdown block is not executed: the project is not trusted on the backend.")
      return false
    }
    return CommandRunnerExtension.launchBlockRunner(project, command, executorId, workingDirectory)
  }

  override suspend fun isProjectTrusted(projectId: ProjectId): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return TrustedProjects.isProjectTrusted(project)
  }

  override suspend fun setProjectTrusted(projectId: ProjectId) {
    val project = projectId.findProjectOrNull() ?: return
    TrustedProjects.setProjectTrusted(project, true)
  }

  private companion object {
    private val LOG = logger<MarkdownCommandRunnerRemoteApiImpl>()
  }
}
