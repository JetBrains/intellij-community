// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.dependency.analyzer.*
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerContributor
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerProject
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle.message
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.idea.maven.model.MavenArtifactNode
import org.jetbrains.idea.maven.model.MavenArtifactState
import org.jetbrains.idea.maven.model.MavenConstants


class MavenDependencyAnalyzerContributor(private val project: Project) : DependencyAnalyzerContributor {

  override fun whenDataChanged(listener: () -> Unit, parentDisposable: Disposable) {
    val projectsManager = MavenProjectsManager.getInstance(project)
    projectsManager.addProjectsTreeListener(object : MavenProjectsTree.Listener {
      override fun resolutionCompleted() {
        listener()
      }
    }, parentDisposable)
  }

  override fun getProjects(): List<DependencyAnalyzerProject> {
    return MavenProjectsManager.getInstance(project)
      .projects
      .map { DAProject(it.path, it.displayName) }
  }

  override fun getDependencyScopes(externalProjectPath: String): List<Dependency.Scope> {
    return listOf(scope(MavenConstants.SCOPE_COMPILE),
                  scope(MavenConstants.SCOPE_PROVIDED),
                  scope(MavenConstants.SCOPE_RUNTIME),
                  scope(MavenConstants.SCOPE_SYSTEM),
                  scope(MavenConstants.SCOPE_IMPORT),
                  scope(MavenConstants.SCOPE_TEST))
  }

  override fun getDependencies(externalProjectPath: String): List<Dependency> {
    return LocalFileSystem.getInstance().findFileByPath(externalProjectPath)
             ?.let { MavenProjectsManager.getInstance(project).findProject(it) }
             ?.let { createDependencyList(it) }
           ?: emptyList()
  }

  private fun createDependencyList(mavenProject: MavenProject): List<Dependency> {
    val root = DAModule(mavenProject.displayName)
    val rootDependency = DADependency(root, scope(MavenConstants.SCOPE_COMPILE), null, emptyList())
    val result = mutableListOf<Dependency>()
    collectDependency(mavenProject.dependencyTree, rootDependency, result)
    return result
  }

  private fun collectDependency(nodes: List<MavenArtifactNode>,
                                parentDependency: Dependency,
                                result: MutableList<Dependency>) {
    for (mavenArtifactNode in nodes) {
      val dependency = DADependency(
        getDependencyData(mavenArtifactNode),
        scope(mavenArtifactNode.originalScope ?: MavenConstants.SCOPE_COMPILE), parentDependency,
        getStatus(mavenArtifactNode))
      result.add(dependency)
      if (mavenArtifactNode.dependencies != null) {
        collectDependency(mavenArtifactNode.dependencies, dependency, result)
      }
    }
  }

  private fun getDependencyData(mavenArtifactNode: MavenArtifactNode): Dependency.Data {
    return DAArtifact(
      mavenArtifactNode.artifact.groupId, mavenArtifactNode.artifact.artifactId, mavenArtifactNode.artifact.version
    )
  }

  private fun getStatus(mavenArtifactNode: MavenArtifactNode): List<Dependency.Status> {
    val status = mutableListOf<Dependency.Status>()
    if (mavenArtifactNode.getState() == MavenArtifactState.CONFLICT) {
      status.add(DAOmitted)
      mavenArtifactNode.relatedArtifact?.version?.also {
        val message = message("external.system.dependency.analyzer.warning.version.conflict", it)
        status.add(DAWarning(message))
      }
    }
    else if (mavenArtifactNode.getState() == MavenArtifactState.DUPLICATE) {
      status.add(DAOmitted)
    }
    if (!mavenArtifactNode.artifact.isResolvedArtifact) {
      status.add(DAWarning(message("external.system.dependency.analyzer.warning.unresolved")))
    }
    return status
  }

  companion object {
    fun scope(name: @NlsSafe String) = DAScope(name, StringUtil.toTitleCase(name))
  }
}