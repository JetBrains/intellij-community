// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.externalSystem.dependency.analyzer.*
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.Module
import com.intellij.ui.treeStructure.SimpleTree
import org.jetbrains.idea.maven.navigator.MavenProjectsStructure.*
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil


class ViewDependencyAnalyzerAction : AbstractDependencyAnalyzerAction<MavenSimpleNode>() {

  override fun getSystemId(e: AnActionEvent): ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun getSelectedData(e: AnActionEvent): MavenSimpleNode? {
    val data = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    return (data as? SimpleTree)?.selectedNode as? MavenSimpleNode
  }

  override fun getModule(e: AnActionEvent, selectedData: MavenSimpleNode): Module? {
    val project = e.project ?: return null
    val projectNode = selectedData.findNode(ProjectNode::class.java) ?: return null
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    return mavenProjectsManager.findModule(projectNode.mavenProject)
  }

  override fun getDependencyData(e: AnActionEvent, selectedData: MavenSimpleNode): DependencyAnalyzerDependency.Data? {
    return when (selectedData) {
      is DependencyNode -> {
        DAArtifact(
          selectedData.artifact.groupId,
          selectedData.artifact.artifactId,
          selectedData.artifact.version
        )
      }
      is ProjectNode -> {
        DAModule(selectedData.mavenProject.displayName)
      }
      else -> null
    }
  }

  override fun getDependencyScope(e: AnActionEvent, selectedData: MavenSimpleNode): String? {
    if (selectedData is DependencyNode) {
      return selectedData.artifact.scope
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

class ProjectViewDependencyAnalyzerAction : AbstractDependencyAnalyzerAction<Module>() {

  override fun getSystemId(e: AnActionEvent): ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun getSelectedData(e: AnActionEvent): Module? {
    val project = e.project ?: return null
    val module = e.getData(PlatformCoreDataKeys.MODULE) ?: return null
    if (MavenProjectsManager.getInstance(project).isMavenizedModule(module)) {
      return module
    }
    return null
  }

  override fun getModule(e: AnActionEvent, selectedData: Module): Module {
    return selectedData
  }

  override fun getDependencyData(e: AnActionEvent, selectedData: Module): DependencyAnalyzerDependency.Data {
    return DAModule(selectedData.name)
  }

  override fun getDependencyScope(e: AnActionEvent, selectedData: Module) = null
}
