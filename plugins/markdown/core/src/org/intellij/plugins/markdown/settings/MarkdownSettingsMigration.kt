// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.settings

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.settings.pandoc.PandocApplicationSettings
import org.intellij.plugins.markdown.util.MarkdownPluginScope

internal class MarkdownSettingsMigration: ProjectActivity, TrustedProjectsListener {
  override suspend fun execute(project: Project) {
    if (project.isDefault) return
    val markdownSettings = MarkdownSettings.getInstanceAsync()
    withContext(Dispatchers.EDT) {
      if (TrustedProjects.isProjectTrusted(project)) {
        markdownSettings.reconcileWithProject(project)
      }
    }
    serviceAsync<PandocApplicationSettings>().reconcileWithProject(project)
  }

  override fun onProjectTrusted(project: Project) {
    MarkdownPluginScope.scope(project).launch { execute(project) }
  }
}
