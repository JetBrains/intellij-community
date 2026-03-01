// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.ide.plugins.DependencyInformation
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class MavenDependencyCollector : DependencyCollector {

  override suspend fun collectDependencies(project: Project): Set<DependencyInformation> {
    val projects = withContext(Dispatchers.IO) {
      MavenProjectsManager.getInstance(project).projects
    }
    return readAction {
      projects.asSequence()
        .flatMap { p -> p.dependencies }
        .map { it.groupId to it.artifactId }
        .distinct()
        .map { (g, a) -> DependencyInformation("$g:$a") }
        .toSet()
    }
  }
}

internal class MavenDependencyUpdater(private val project: Project) : MavenImportListener {

  override fun importFinished(importedProjects: Collection<MavenProject>, newModules: List<Module>) {
    PluginAdvertiserService.getInstance(project).rescanDependencies()
  }
}