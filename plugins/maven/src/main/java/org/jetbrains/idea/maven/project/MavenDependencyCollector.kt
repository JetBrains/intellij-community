// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.openapi.project.Project

class MavenDependencyCollector : DependencyCollector {
  override val dependencyKind: String
    get() = "java"

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