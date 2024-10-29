// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.externalSystem.dependency.analyzer.*
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.module.Module
import com.intellij.ui.treeStructure.SimpleTree
import org.jetbrains.idea.maven.navigator.structure.ArtifactNode
import org.jetbrains.idea.maven.navigator.structure.MavenNode
import org.jetbrains.idea.maven.navigator.structure.MavenProjectNode
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil


class ViewDependencyAnalyzerAction : AbstractDependencyAnalyzerAction<MavenNode>() {

  override fun getSystemId(e: AnActionEvent): ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun getSelectedData(e: AnActionEvent): MavenNode? {
    val data = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    return (data as? SimpleTree)?.selectedNode as? MavenNode
  }

  override fun getModule(e: AnActionEvent, selectedData: MavenNode): Module? {
    val project = e.project ?: return null
    val projectNode = selectedData.findProjectNode() ?: return null
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    return mavenProjectsManager.findModule(projectNode.mavenProject)
  }

  override fun getDependencyData(e: AnActionEvent, selectedData: MavenNode): DependencyAnalyzerDependency.Data? {
    return when (selectedData) {
      is ArtifactNode -> {
        DAArtifact(
          selectedData.artifact.groupId,
          selectedData.artifact.artifactId,
          selectedData.artifact.version
        )
      }
      is MavenProjectNode -> {
        DAModule(selectedData.mavenProject.displayName)
      }
      else -> null
    }
  }

  override fun getDependencyScope(e: AnActionEvent, selectedData: MavenNode): String? {
    if (selectedData is ArtifactNode) {
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

  override fun isEnabledAndVisible(e: AnActionEvent): Boolean {
    return super.isEnabledAndVisible(e)
           && !(e.isFromContextMenu && e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY) == null)
  }
}
