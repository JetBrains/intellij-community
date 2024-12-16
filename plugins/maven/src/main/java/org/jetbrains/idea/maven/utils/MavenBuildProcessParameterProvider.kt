// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import java.nio.file.Path

class MavenBuildProcessParameterProvider(private val project: Project) : BuildProcessParametersProvider() {

  override fun getPathParameters(): List<Pair<String, Path>> {
    val projectFilePath = project.projectFilePath ?: return emptyList()
    val pathMacro = MavenPathMacroContributor.getPathToMavenHome(projectFilePath)
    return listOf(Pair.create("-Dide.compiler.maven.path.to.home=", Path.of(pathMacro)))
  }
}