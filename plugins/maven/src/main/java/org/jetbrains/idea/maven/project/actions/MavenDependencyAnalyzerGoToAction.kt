// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.dependency.analyzer.DAArtifact
import com.intellij.openapi.externalSystem.dependency.analyzer.DAModule
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil
import org.jetbrains.idea.maven.project.MavenDependencyAnalyzerContributor
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency

class MavenDependencyAnalyzerGoToAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dependency = e.getData(DependencyAnalyzerView.DEPENDENCY) ?: return
    val project = e.project ?: return
    val parent = dependency.parent ?: return
    val mavenId = getMavenId(dependency.data) ?: return
    if (mavenId.groupId == null || mavenId.artifactId == null) return;

    val artifactFile = getArtifactFile(project, parent.data) ?: return
    val navigatable = MavenNavigationUtil.createNavigatableForDependency(project, artifactFile, mavenId.groupId!!, mavenId.artifactId!!)
    if (navigatable.canNavigate()) {
      navigatable.navigate(true)
    }
  }

  override fun update(e: AnActionEvent) {
    val systemId = e.getData(DependencyAnalyzerView.EXTERNAL_SYSTEM_ID)
    val dependency = e.getData(DependencyAnalyzerView.DEPENDENCY)
    e.presentation.isEnabledAndVisible =
      systemId == MavenUtil.SYSTEM_ID &&
      e.project != null &&
      getMavenId(dependency?.data) != null &&
      getArtifactFile(e.project!!, dependency?.parent?.data) != null
  }

  private fun getArtifactFile(project: Project, artifactParent: Dependency.Data?): VirtualFile? {
    val mavenId = getMavenId(artifactParent) ?: return null
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(mavenId)
    return mavenProject?.file ?: MavenNavigationUtil.getArtifactFile(project, mavenId)
  }

  private fun getMavenId(dependencyData: Dependency.Data?): MavenId? {
    return when (dependencyData) {
      is DAArtifact -> MavenId(dependencyData.groupId, dependencyData.artifactId, dependencyData.version)
      is DAModule -> dependencyData.getUserData(MavenDependencyAnalyzerContributor.MAVEN_ARTIFACT_ID)
      else -> null
    }
  }

  init {
    templatePresentation.icon = null
    templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.go.to", "pom.xml")
  }
}