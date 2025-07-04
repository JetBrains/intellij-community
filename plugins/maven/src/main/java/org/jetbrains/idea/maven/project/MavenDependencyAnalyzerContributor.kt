// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.dependency.analyzer.*
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency.Scope.Type.CUSTOM
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency.Scope.Type.STANDARD
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle.message
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.model.MavenArtifactNode
import org.jetbrains.idea.maven.model.MavenArtifactState
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.model.MavenId
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency


class MavenDependencyAnalyzerContributor(private val project: Project) : DependencyAnalyzerContributor {

  override fun whenDataChanged(listener: () -> Unit, parentDisposable: Disposable) {
    val projectsManager = MavenProjectsManager.getInstance(project)
    projectsManager.addProjectsTreeListener(object : MavenProjectsTree.Listener {
      override fun projectResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>) {
        listener()
      }
    }, parentDisposable)
  }

  override fun getProjects(): List<DependencyAnalyzerProject> {
    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    val externalProjects = ArrayList<DependencyAnalyzerProject>()
    for (mavenProject in mavenProjectsManager.projects) {
      val module = runReadAction {
        mavenProjectsManager.findModule(mavenProject)
      } ?: continue
      externalProjects.add(DAProject(module, mavenProject.displayName))
    }
    return externalProjects
  }

  override fun getDependencyScopes(externalProject: DependencyAnalyzerProject): List<Dependency.Scope> {
    return STANDARD_SCOPES.map(::scope)
  }

  override fun getDependencies(externalProject: DependencyAnalyzerProject): List<Dependency> {
    val projectsManager = MavenProjectsManager.getInstance(project)
    val mavenProject = projectsManager.findProject(externalProject.module) ?: return emptyList()
    return createDependencyList(mavenProject)
  }

  private fun createDependencyList(mavenProject: MavenProject): List<Dependency> {
    val root = DAModule(mavenProject.displayName)
    val mavenId = mavenProject.mavenId
    root.putUserData(MAVEN_ARTIFACT_ID, MavenId(mavenId.groupId, mavenId.artifactId, mavenId.version))
    val rootDependency = DADependency(root, DEFAULT_SCOPE, null, emptyList())
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
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(mavenArtifactNode.artifact)
    val daArtifact = DAArtifact(
      mavenArtifactNode.artifact.groupId, mavenArtifactNode.artifact.artifactId, mavenArtifactNode.artifact.version
    )
    if (mavenProject != null) {
      val daModule = DAModule(mavenProject.displayName)
      daModule.putUserData(MAVEN_ARTIFACT_ID, MavenId(daArtifact.groupId, daArtifact.artifactId, daArtifact.version))
      return daModule
    }
    return daArtifact
  }

  private fun getStatus(mavenArtifactNode: MavenArtifactNode): List<Dependency.Status> {
    val status = mutableListOf<Dependency.Status>()
    if (mavenArtifactNode.state == MavenArtifactState.CONFLICT) {
      status.add(DAOmitted)
      mavenArtifactNode.relatedArtifact?.version?.also {
        val title = message("external.system.dependency.analyzer.warning.version.conflict.title", it)
        val message = message("external.system.dependency.analyzer.warning.version.conflict", it)
        status.add(DAWarning(title, message))
      }
    }
    else if (mavenArtifactNode.state == MavenArtifactState.DUPLICATE) {
      status.add(DAOmitted)
    }
    if (!mavenArtifactNode.artifact.isResolvedArtifact) {
      val title = message("external.system.dependency.analyzer.warning.unresolved.title")
      val message = message("external.system.dependency.analyzer.warning.unresolved")
      status.add(DAWarning(title, message))
    }
    return status
  }

  companion object {

    val MAVEN_ARTIFACT_ID: Key<MavenId?> = Key.create<MavenId>("MavenDependencyAnalyzerContributor.MavenId")

    internal val DEFAULT_SCOPE = DAScope(message("external.system.dependency.analyzer.scope.default"))

    private val STANDARD_SCOPES = listOf(
      MavenConstants.SCOPE_COMPILE,
      MavenConstants.SCOPE_PROVIDED,
      MavenConstants.SCOPE_RUNTIME,
      MavenConstants.SCOPE_SYSTEM,
      MavenConstants.SCOPE_IMPORT,
      MavenConstants.SCOPE_TEST
    )

    private fun scope(name: @Nls String): DAScope {
      return DAScope(name, type = scopeType(name))
    }

    private fun scopeType(name: String): Dependency.Scope.Type {
      return if (name in STANDARD_SCOPES) STANDARD else CUSTOM
    }
  }
}