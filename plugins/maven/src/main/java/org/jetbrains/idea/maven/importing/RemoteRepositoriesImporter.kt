// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.utils.MavenUtil

class RemoteRepositoriesImporter : MavenImporter("", "") {


  override fun processChangedModulesOnly(): Boolean = false

  override fun isApplicable(mavenProject: MavenProject?): Boolean {
    return true
  }

  override fun preProcess(module: Module?,
                          mavenProject: MavenProject?,
                          changes: MavenProjectChanges?,
                          modifiableModelsProvider: IdeModifiableModelsProvider?) {
    if (module == null || mavenProject == null) {
      return
    }

    val repoConfig = RemoteRepositoriesConfiguration.getInstance(module.project)
    val repositories: MutableCollection<RemoteRepositoryDescription> =
      hashSetOf<RemoteRepositoryDescription>().apply { addAll(repoConfig.repositories) }

    mavenProject.remoteRepositories.mapTo(repositories) {
      RemoteRepositoryDescription(it.id, it.name ?: it.id, mirror(it.id, it.url, module)).also {
        LOG.debug("Imported remote repository ${it.id}/${it.name} at ${it.url}")
      }
    }

    repoConfig.repositories = repositories.toMutableList()
  }

  private fun mirror(id: String, url: String, module: Module): String {
    val settingsFile = MavenWorkspaceSettingsComponent.getInstance(
      module.project).settings.generalSettings.effectiveUserSettingsIoFile

    return MavenUtil.getMirroredUrl(settingsFile, url, id)
  }

  companion object {
    val LOG = Logger.getInstance(RemoteRepositoriesImporter::class.java)
  }
}
