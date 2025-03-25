// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.uv

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.observation.ActivityKey
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.observation.trackActivityBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.jetbrains.python.PyBundle
import com.jetbrains.python.projectModel.readProjectModelGraph
import com.jetbrains.python.projectModel.uv.UvLinkAction.CoroutineScopeService.Companion.coroutineScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.Nls
import java.nio.file.Path

/**
 * Discovers and links as managed by uv all relevant project roots and saves them in `.idea/uv.xml`.
 * For a tree of nested uv projects, only the topmost directories are linked.
 */
class UvLinkAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val uvSettings = project.service<UvSettings>()
    val basePath = project.basePath ?: return
    project.trackActivityBlocking(UvLinkActivityKey) {
      project.coroutineScope.launchTracked {
        val allProjectRoots = withBackgroundProgress(project = project, title = PyBundle.message("python.project.model.progress.title.discovering.uv.projects")) {
          readProjectModelGraph(Path.of(basePath)).roots.map { it.root }
        }
        uvSettings.setLinkedProjects(allProjectRoots)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = Registry.`is`("python.project.model.uv")
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  object UvLinkActivityKey : ActivityKey {
    override val presentableName: @Nls String
      get() = PyBundle.message("python.project.model.activity.key.uv.link")
  }

  @Service(Service.Level.PROJECT)
  private class CoroutineScopeService(private val coroutineScope: CoroutineScope) {
    companion object {
      val Project.coroutineScope: CoroutineScope
        get() = service<CoroutineScopeService>().coroutineScope
    }
  }
}