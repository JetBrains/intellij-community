// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.maven2

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.ide.plugins.DependencyInformation
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.MavenVersionSupportUtil
import org.jetbrains.idea.maven.project.MavenProjectsManager

internal class Maven2VersionDependencyCollector : DependencyCollector {
  override suspend fun collectDependencies(project: Project): List<DependencyInformation> {
    val manager = MavenProjectsManager.getInstance(project)
    if (!manager.isMavenizedProject) return emptyList()
    if (MavenVersionSupportUtil.isMaven2Used(project)) return mavenDependencyInformation
    return emptyList()
  }
}

private val mavenDependencyInformation = listOf(DependencyInformation("maven2"))
