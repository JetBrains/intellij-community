// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService

internal class MavenDependencyCollector : DependencyCollector {

  override suspend fun collectDependencies(project: Project): Set<String> {
    return readAction {
      MavenProjectsManager.getInstance(project).projects.asSequence()
        .flatMap { p -> p.dependencies }
        .map { it.groupId to it.artifactId }
        .distinct()
        .map { (g, a) -> "$g:$a" }
        .toSet()
    }
  }
}

internal class MavenDependencyUpdater(private val project: Project) : MavenImportListener {

  override fun importFinished(importedProjects: Collection<MavenProject>, newModules: List<Module>) {
    PluginAdvertiserService.getInstance(project).rescanDependencies()
  }
}