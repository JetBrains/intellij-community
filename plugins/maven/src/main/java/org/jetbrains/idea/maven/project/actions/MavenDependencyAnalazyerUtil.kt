// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil
import org.jetbrains.idea.maven.project.MavenDependencyAnalyzerContributor
import org.jetbrains.idea.maven.project.MavenProjectsManager


internal fun getArtifactFile(project: Project, dependencyData: DependencyAnalyzerDependency.Data?): VirtualFile? {
  val mavenId = getMavenId(dependencyData) ?: return null
  val mavenProject = MavenProjectsManager.getInstance(project).findProject(mavenId)
  return mavenProject?.file ?: MavenNavigationUtil.getArtifactFile(project, mavenId)
}

internal fun getMavenId(dependencyData: DependencyAnalyzerDependency.Data?): MavenId? {
  return when (dependencyData) {
    is DependencyAnalyzerDependency.Data.Artifact -> MavenId(dependencyData.groupId, dependencyData.artifactId, dependencyData.version)
    is DependencyAnalyzerDependency.Data.Module -> dependencyData.getUserData(MavenDependencyAnalyzerContributor.MAVEN_ARTIFACT_ID)
    else -> null
  }
}