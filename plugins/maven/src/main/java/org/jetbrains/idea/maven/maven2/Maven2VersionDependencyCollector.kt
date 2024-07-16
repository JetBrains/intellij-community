// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.maven2

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.MavenVersionSupportUtil
import org.jetbrains.idea.maven.project.MavenProjectsManager

internal class Maven2VersionDependencyCollector : DependencyCollector {
  override suspend fun collectDependencies(project: Project): List<String> {
    val manager = MavenProjectsManager.getInstance(project)
    if (!manager.isMavenizedProject) return emptyList()
    if (MavenVersionSupportUtil.isMaven2Used(project)) return listOf("maven2")
    return emptyList()
  }
}
