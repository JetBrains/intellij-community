// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.compilation

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.RemotePathTransformerFactory
import org.jetbrains.idea.maven.utils.MavenFilteredJarUtils
import org.jetbrains.jps.maven.model.impl.MavenProjectConfiguration

internal class FilteredJarConfigGenerator(
  private val fileIndex: ProjectFileIndex,
  private val mavenProjectsManager: MavenProjectsManager,
  private val transformer: RemotePathTransformerFactory.Transformer,
  private val config: MavenProjectConfiguration,
  private val mavenProject: MavenProject,
) {
  fun generateAdditionalJars() {
    if (!Registry.`is`("maven.build.additional.jars")) {
      return
    }
    if ("pom" == mavenProject.packaging) return;
    MavenFilteredJarUtils.getAllFilteredConfigurations(mavenProjectsManager, mavenProject).forEach { jarConfig ->
      config.jarsConfiguration[jarConfig.name] = jarConfig
    }
  }
}