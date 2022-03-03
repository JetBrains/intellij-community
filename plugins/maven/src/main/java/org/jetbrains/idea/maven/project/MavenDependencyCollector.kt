// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService

class MavenDependencyCollector : DependencyCollector {
  override fun collectDependencies(project: Project): List<String> {
    val result = mutableSetOf<String>()
    for (mavenProject in MavenProjectsManager.getInstance(project).projects) {
      for (dependency in mavenProject.dependencies) {
        result.add(dependency.groupId + ":" + dependency.artifactId)
      }
    }
    return result.toList()
  }
}

class MavenDependencyUpdater(private val project: Project) : MavenImportListener {
  override fun importFinished(importedProjects: Collection<MavenProject>, newModules: List<Module>) {
    ApplicationManager.getApplication().executeOnPooledThread {
      PluginAdvertiserService.getInstance().rescanDependencies(project)
    }
  }
}