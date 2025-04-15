// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.projectModel.poetry

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.observation.ActivityKey
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.observation.trackActivityBlocking
import com.jetbrains.python.PyBundle
import com.jetbrains.python.projectModel.poetry.PoetrySyncAction.CoroutineScopeService.Companion.coroutineScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.Nls

/**
 * Forcibly syncs all *already linked* Poetry projects, overriding their workspace models.
 */
class PoetrySyncAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    project.trackActivityBlocking(PoetryActivityKey) {
      project.coroutineScope.launchTracked {
        PoetryProjectResolver.syncAllPoetryProjects(project = project)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = Registry.`is`("python.project.model.poetry")
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  object PoetryActivityKey : ActivityKey {
    override val presentableName: @Nls String
      get() = PyBundle.message("python.project.model.activity.key.poetry.sync")
  }

  @Service(Service.Level.PROJECT)
  private class CoroutineScopeService(private val coroutineScope: CoroutineScope) {
    companion object {
      val Project.coroutineScope: CoroutineScope
        get() = service<CoroutineScopeService>().coroutineScope
    }
  }
}