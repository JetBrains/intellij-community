// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.nio.file.Path

@ApiStatus.Internal
class MavenRemoteRepositoriesConfigurator : MavenWorkspaceConfigurator {
  private val COLLECTED_REPOSITORIES = Key.create<MutableSet<RemoteRepositoryDescription>>("COLLECTED_REPOSITORIES")

  override fun beforeModelApplied(context: MavenWorkspaceConfigurator.MutableModelContext) {
    val mavenProjects = context.mavenProjectsWithModules.map { it.mavenProject }
    val repositories = collectRepositoriesForMavenProjects(context.project, mavenProjects)
    COLLECTED_REPOSITORIES.set(context, repositories)
  }

  override fun afterModelApplied(context: MavenWorkspaceConfigurator.AppliedModelContext) {
    COLLECTED_REPOSITORIES.get(context)?.let { applyRepositories(context.project, it) }
  }

  private fun collectRepositoriesForMavenProjects(project: Project,
                                                  mavenProjects: Sequence<MavenProject>): MutableSet<RemoteRepositoryDescription> {
    val settingsFile = runReadAction {
      MavenWorkspaceSettingsComponent.getInstance(project).settings.generalSettings.effectiveUserSettingsIoFile
    }

    return mavenProjects.flatMap { mavenProject ->
      mavenProject.remoteRepositories.asSequence().map { repo ->
        RemoteRepositoryDescription(repo.id, repo.name ?: repo.id, mirror(repo.id, repo.url, settingsFile)).also {
          LOG.trace("Imported remote repository from ${mavenProject.mavenId}: ${it.id}/${it.name} at ${it.url}")
        }
      }
    }.toHashSet()
  }

  private fun applyRepositories(project: Project, mavenRepositories: MutableSet<RemoteRepositoryDescription>) {
    val repoConfig = RemoteRepositoriesConfiguration.getInstance(project)
    mavenRepositories.addAll(repoConfig.repositories)
    repoConfig.repositories = mavenRepositories.toList()
  }

  private fun mirror(id: String, url: String, settingsFile: Path?): String {
    return MavenUtil.getMirroredUrl(settingsFile, url, id)
  }

  companion object {
    val LOG = Logger.getInstance(MavenRemoteRepositoriesConfigurator::class.java)
  }
}
