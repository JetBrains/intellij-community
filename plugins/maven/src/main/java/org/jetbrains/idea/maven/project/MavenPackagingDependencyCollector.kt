// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.openapi.project.Project

internal class MavenPackagingDependencyCollector : DependencyCollector {
  override fun collectDependencies(project: Project): List<String> {
    val result = mutableSetOf<String>()
    for (mavenProject in MavenProjectsManager.getInstance(project).projects) {
      val packaging = mavenProject.packaging
      if (packaging.isNotBlank()
          && packaging != "jar"
          && packaging != "pom") {
        result.add(packaging)
      }
    }
    return result.toList()
  }
}