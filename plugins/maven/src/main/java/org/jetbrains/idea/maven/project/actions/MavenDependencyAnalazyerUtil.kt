// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency
import com.intellij.openapi.module.Module
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

fun getUnifiedCoordinates(dependency: DependencyAnalyzerDependency): UnifiedCoordinates? {
  return when (val data = dependency.data) {
    is DependencyAnalyzerDependency.Data.Artifact -> getUnifiedCoordinates(data)
    is DependencyAnalyzerDependency.Data.Module -> getUnifiedCoordinates(data)
  }
}

fun getUnifiedCoordinates(data: DependencyAnalyzerDependency.Data.Artifact): UnifiedCoordinates {
  return UnifiedCoordinates(data.groupId, data.artifactId, data.version)
}

fun getUnifiedCoordinates(data: DependencyAnalyzerDependency.Data.Module): UnifiedCoordinates? {
  val mavenId = data.getUserData(MavenDependencyAnalyzerContributor.MAVEN_ARTIFACT_ID) ?: return null
  return UnifiedCoordinates(mavenId.groupId, mavenId.artifactId, mavenId.version)
}

fun getParentModule(project: Project, dependency: DependencyAnalyzerDependency): Module? {
  val parentData = dependency.parent?.data as? DependencyAnalyzerDependency.Data.Module ?: return null
  return getModule(project, parentData)
}

fun getModule(project: Project, data: DependencyAnalyzerDependency.Data.Module): Module? {
  val mavenId = getMavenId(data) ?: return null
  val projectsManager = MavenProjectsManager.getInstance(project)
  val mavenProject = projectsManager.findProject(mavenId) ?: return null
  return projectsManager.findModule(mavenProject)
}
