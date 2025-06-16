// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

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
import com.jetbrains.python.PyBundle
import com.jetbrains.python.projectModel.poetry.PoetryLinkAction.CoroutineScopeService.Companion.coroutineScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.Nls

/**
 * Discovers and links as managed by Poetry all relevant project roots and saves them in `.idea/poetry.xml`.
 * For a tree of nested poetry projects, only the topmost directories are linked.
 */
class PoetryLinkAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val basePath = project.basePath ?: return
    project.trackActivityBlocking(PoetryLinkActivityKey) {
      project.coroutineScope.launchTracked {
        PoetryProjectModelService.linkAllProjectModelRoots(project, basePath)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = Registry.`is`("python.project.model.poetry")
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  object PoetryLinkActivityKey : ActivityKey {
    override val presentableName: @Nls String
      get() = PyBundle.message("python.project.model.activity.key.poetry.link")
  }

  @Service(Service.Level.PROJECT)
  private class CoroutineScopeService(private val coroutineScope: CoroutineScope) {
    companion object {
      val Project.coroutineScope: CoroutineScope
        get() = service<CoroutineScopeService>().coroutineScope
    }
  }
}