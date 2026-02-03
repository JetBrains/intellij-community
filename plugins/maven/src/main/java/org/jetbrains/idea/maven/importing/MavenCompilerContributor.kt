// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectResolutionContributor
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper

internal class MavenCompilerContributor : MavenProjectResolutionContributor {
  override suspend fun onMavenProjectResolved(
    project: Project,
    mavenProject: MavenProject,
    embedder: MavenEmbedderWrapper
  ) {
    if (!isApplicable(mavenProject)) return
    if (!Registry.`is`("maven.import.compiler.arguments", true) || !MavenProjectsManager.getInstance(project).importingSettings.isAutoDetectCompiler) return

    val defaultCompilerExtension = MavenCompilerExtension.EP_NAME.extensions.find {
      it.resolveDefaultCompiler(project, mavenProject, embedder)
    }
    if (project.getUserData(DEFAULT_COMPILER_EXTENSION) == null) {
      project.putUserData(DEFAULT_COMPILER_EXTENSION, defaultCompilerExtension)
    }
  }

  private fun isApplicable(mavenProject: MavenProject): Boolean {
    return mavenProject.findPlugin("org.apache.maven.plugins", "maven-compiler-plugin") != null
  }
}