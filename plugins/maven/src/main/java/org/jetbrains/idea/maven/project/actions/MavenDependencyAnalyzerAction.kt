// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.externalSystem.dependency.analyzer.AbstractDependencyAnalyzerAction
import com.intellij.openapi.externalSystem.dependency.analyzer.DAArtifact
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerView
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.SimpleTree
import org.jetbrains.idea.maven.navigator.MavenProjectsStructure
import org.jetbrains.idea.maven.project.MavenDependencyAnalyzerContributor
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency

class MavenDependencyAnalyzerAction : AbstractDependencyAnalyzerAction() {
  override fun getSystemId(e: AnActionEvent): ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun setSelectedState(e: AnActionEvent, view: DependencyAnalyzerView) {
    val project = e.project ?: return
    val selectedNode = getSelectedNode(e) ?: return
    val externalProjectPath = getExternalProjectPath(project, selectedNode) ?: return
    val dependencyData = getDependencyData(selectedNode)
    val dependencyScope = getDependencyScope(selectedNode)
    if (dependencyData != null && dependencyScope != null) {
      view.setSelectedDependency(externalProjectPath, dependencyData, dependencyScope)
    }
    else if (dependencyData != null) {
      view.setSelectedDependency(externalProjectPath, dependencyData)
    }
    else {
      view.setSelectedExternalProject(externalProjectPath)
    }
  }

  private fun getSelectedNode(e: AnActionEvent): SimpleNode? {
    val data = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    return (data as? SimpleTree)?.selectedNode
  }

  private fun getExternalProjectPath(project: Project, selectedNode: SimpleNode): String? {
    if (selectedNode is MavenProjectsStructure.ProjectNode) {
      return selectedNode.mavenProject.path
    }
    if (selectedNode is MavenProjectsStructure.MavenSimpleNode) {
      val projectNode = selectedNode.findParent(MavenProjectsStructure.ProjectNode::class.java)
      if (projectNode != null) {
        return projectNode.mavenProject.path
      }
    }
    return MavenProjectsManager.getInstance(project).rootProjects.firstOrNull()?.path
  }

  private fun getDependencyData(selectedNode: SimpleNode): Dependency.Data? {
    if (selectedNode is MavenProjectsStructure.DependencyNode) {
      return DAArtifact(
        selectedNode.artifact.groupId,
        selectedNode.artifact.artifactId,
        selectedNode.artifact.version
      )
    }
    return null
  }

  private fun getDependencyScope(selectedNode: SimpleNode): Dependency.Scope? {
    if (selectedNode is MavenProjectsStructure.DependencyNode) {
      return MavenDependencyAnalyzerContributor.scope(
        selectedNode.artifact.scope
      )
    }
    return null
  }
}