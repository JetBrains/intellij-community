// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.externalSystem.dependency.analyzer.AbstractDependencyAnalyzerAction
import com.intellij.openapi.externalSystem.dependency.analyzer.DAArtifact
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.SimpleTree
import org.jetbrains.idea.maven.navigator.MavenProjectsStructure
import org.jetbrains.idea.maven.project.MavenDependencyAnalyzerContributor
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenDependencyAnalyzerAction : AbstractDependencyAnalyzerAction() {

  override fun getSystemId(e: AnActionEvent): ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun getContributors(): List<Contributor<*>> =
    listOf(
      MavenToolwindowContributor(),
      ProjectViewContributor()
    )

  private class MavenToolwindowContributor : Contributor<SimpleNode> {

    override fun getSelectedData(e: AnActionEvent): SimpleNode? {
      val data = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
      return (data as? SimpleTree)?.selectedNode
    }

    override fun getExternalProjectPath(e: AnActionEvent, selectedData: SimpleNode): String? {
      if (selectedData is MavenProjectsStructure.ProjectNode) {
        return selectedData.mavenProject.path
      }
      if (selectedData is MavenProjectsStructure.MavenSimpleNode) {
        val projectNode = selectedData.findParent(MavenProjectsStructure.ProjectNode::class.java)
        if (projectNode != null) {
          return projectNode.mavenProject.path
        }
      }
      val project = e.project ?: return null
      return MavenProjectsManager.getInstance(project).rootProjects.firstOrNull()?.path
    }

    override fun getDependencyData(e: AnActionEvent, selectedData: SimpleNode): DependencyAnalyzerDependency.Data? {
      if (selectedData is MavenProjectsStructure.DependencyNode) {
        return DAArtifact(
          selectedData.artifact.groupId,
          selectedData.artifact.artifactId,
          selectedData.artifact.version
        )
      }
      return null
    }

    override fun getDependencyScope(e: AnActionEvent, selectedData: SimpleNode): DependencyAnalyzerDependency.Scope? {
      if (selectedData is MavenProjectsStructure.DependencyNode) {
        return MavenDependencyAnalyzerContributor.scope(
          selectedData.artifact.scope
        )
      }
      return null
    }
  }

  private class ProjectViewContributor : Contributor<MavenProject> {
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
}