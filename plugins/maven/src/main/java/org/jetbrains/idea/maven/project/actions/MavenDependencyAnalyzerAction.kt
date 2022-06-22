// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.externalSystem.dependency.analyzer.*
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.ui.treeStructure.SimpleTree
import org.jetbrains.idea.maven.navigator.MavenProjectsStructure.*
import org.jetbrains.idea.maven.project.MavenDependencyAnalyzerContributor
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil


class ViewDependencyAnalyzerAction : AbstractDependencyAnalyzerAction<MavenSimpleNode>() {

  override fun getSystemId(e: AnActionEvent): ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun getSelectedData(e: AnActionEvent): MavenSimpleNode? {
    val data = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    return (data as? SimpleTree)?.selectedNode as? MavenSimpleNode
  }

  override fun getExternalProjectPath(e: AnActionEvent, selectedData: MavenSimpleNode): String? {
    if (selectedData is ProjectNode) {
      return selectedData.mavenProject.path
    }
    val projectNode = selectedData.findParent(ProjectNode::class.java)
    return projectNode?.mavenProject?.path
  }

  override fun getDependencyData(e: AnActionEvent, selectedData: MavenSimpleNode): DependencyAnalyzerDependency.Data? {
    if (selectedData is DependencyNode) {
      return DAArtifact(
        selectedData.artifact.groupId,
        selectedData.artifact.artifactId,
        selectedData.artifact.version
      )
    }
    return null
  }

  override fun getDependencyScope(e: AnActionEvent, selectedData: MavenSimpleNode): DependencyAnalyzerDependency.Scope? {
    if (selectedData is DependencyNode) {
      return MavenDependencyAnalyzerContributor.scope(
        selectedData.artifact.scope
      )
    }
    return null
  }
}

class NavigatorDependencyAnalyzerAction : DependencyAnalyzerAction() {

  private val viewAction = ViewDependencyAnalyzerAction()

  override fun getSystemId(e: AnActionEvent): ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun isEnabledAndVisible(e: AnActionEvent) = true

  override fun setSelectedState(view: DependencyAnalyzerView, e: AnActionEvent) {
    viewAction.setSelectedState(view, e)
  }
}

class ProjectViewDependencyAnalyzerAction : AbstractDependencyAnalyzerAction<MavenProject>() {

  override fun getSystemId(e: AnActionEvent): ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun getSelectedData(e: AnActionEvent): MavenProject? {
    val project = e.project ?: return null
    val modules = e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY) ?: return null
    val projectsManager = MavenProjectsManager.getInstanceIfCreated(project) ?: return null
    return modules.asSequence()
      .mapNotNull { projectsManager.findProject(it) }
      .firstOrNull()
  }

  override fun getExternalProjectPath(e: AnActionEvent, selectedData: MavenProject): String {
    return selectedData.path
  }

  override fun getDependencyData(e: AnActionEvent, selectedData: MavenProject) = null

  override fun getDependencyScope(e: AnActionEvent, selectedData: MavenProject) = null
}
