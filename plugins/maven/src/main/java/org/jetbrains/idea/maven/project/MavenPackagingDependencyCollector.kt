// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.openapi.project.Project

internal class MavenPackagingDependencyCollector : DependencyCollector {

  override fun collectDependencies(project: Project): Set<String> {
    return MavenProjectsManager.getInstance(project)
      .projects
      .asSequence()
      .map { it.packaging }
      .filter { packaging ->
        packaging.isNotBlank()
        && packaging != "jar"
        && packaging != "pom"
      }.toSet()
  }
}