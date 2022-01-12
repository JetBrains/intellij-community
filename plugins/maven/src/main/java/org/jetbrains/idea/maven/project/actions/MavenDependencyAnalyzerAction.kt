// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.externalSystem.dependency.analyzer.AbstractDependencyAnalyzerAction
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerContributor
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.ui.treeStructure.SimpleTree
import org.jetbrains.idea.maven.navigator.MavenProjectsStructure
import org.jetbrains.idea.maven.project.MavenDependencyAnalyzerContributor
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenDependencyAnalyzerAction : AbstractDependencyAnalyzerAction() {
  override fun getSystemId(e: AnActionEvent): ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun getExternalProjectPath(e: AnActionEvent): String? {
    val data = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    if (data is SimpleTree && data.selectedNode is MavenProjectsStructure.ProjectNode) {
      return (data.selectedNode as MavenProjectsStructure.ProjectNode).mavenProject.path
    }
    if (data is SimpleTree && data.selectedNode is MavenProjectsStructure.MavenSimpleNode) {
      val selectedNode = data.selectedNode as MavenProjectsStructure.MavenSimpleNode
      val projectNode = selectedNode.findParent(MavenProjectsStructure.ProjectNode::class.java)
      if (projectNode != null) {
        return projectNode.mavenProject.path
      }
    }
    return e.project?.let { MavenProjectsManager.getInstance(it).projectsFiles.firstOrNull()?.path }
  }

  override fun getDependency(e: AnActionEvent): DependencyAnalyzerContributor.Dependency? {
    val data = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    if (data is SimpleTree && data.selectedNode is MavenProjectsStructure.DependencyNode) {
      val selectedNode = data.selectedNode as MavenProjectsStructure.DependencyNode

      return DependencyAnalyzerContributor.Dependency(
        DependencyAnalyzerContributor.Dependency.Data.Artifact(
          selectedNode.artifact.groupId,
          selectedNode.artifact.artifactId,
          selectedNode.artifact.version),
        MavenDependencyAnalyzerContributor.scope(selectedNode.artifact.scope),
        null,
        listOf()
      )
    }
    return null
  }
}