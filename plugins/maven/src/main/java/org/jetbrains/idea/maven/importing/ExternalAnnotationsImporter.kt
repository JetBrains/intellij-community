// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.codeInsight.ExternalAnnotationsArtifactsResolver
import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.project.*

class ExternalAnnotationsImporter : MavenImporter("org.apache.maven.plugins", "maven-compiler-plugin") {

  private val myProcessedLibraries = hashSetOf<MavenArtifact>()

  override fun isApplicable(mavenProject: MavenProject?): Boolean  {
    return super.isApplicable(mavenProject) && Registry.`is`("external.system.import.resolve.annotations")
  }
  override fun processChangedModulesOnly(): Boolean = false

  override fun process(modifiableModelsProvider: IdeModifiableModelsProvider?,
                       module: Module?,
                       rootModel: MavenRootModelAdapter?,
                       mavenModel: MavenProjectsTree?,
                       mavenProject: MavenProject?,
                       changes: MavenProjectChanges?,
                       mavenProjectToModuleName: MutableMap<MavenProject, String>?,
                       postTasks: MutableList<MavenProjectsProcessorTask>?) {
    // do nothing
  }

  override fun preProcess(module: Module?,
                          mavenProject: MavenProject?,
                          changes: MavenProjectChanges?,
                          modifiableModelsProvider: IdeModifiableModelsProvider?) {
    if (module == null
        || mavenProject == null
        || !MavenProjectsManager.getInstance(module.project).importingSettings.isDownloadAnnotationsAutomatically) {
      return
    }

    val repoConfig = RemoteRepositoriesConfiguration.getInstance(module.project)
    val repositories: MutableCollection<RemoteRepositoryDescription> =
      hashSetOf<RemoteRepositoryDescription>().apply { addAll(repoConfig.repositories) }

    mavenProject.remoteRepositories.mapTo(repositories) { RemoteRepositoryDescription(it.id, it.name ?: it.id, it.url) }

    repoConfig.repositories = repositories.toMutableList()
  }

  override fun postProcess(module: Module,
                           mavenProject: MavenProject,
                           changes: MavenProjectChanges,
                           modifiableModelsProvider: IdeModifiableModelsProvider) {

    val resolver = ExternalAnnotationsArtifactsResolver.EP_NAME.extensionList.firstOrNull() ?: return
    val project = module.project
    val librariesMap = mutableMapOf<MavenArtifact, Library>()

    if (!MavenProjectsManager.getInstance(project).importingSettings.isDownloadAnnotationsAutomatically) {
      return
    }

    mavenProject.dependencies.forEach {
      val library = modifiableModelsProvider.getLibraryByName(it.libraryName)
      if (library != null) {
        librariesMap[it] = library
      }
    }

    val toProcess = librariesMap.filterKeys { myProcessedLibraries.add(it) }
    val totalSize = toProcess.size
    var count = 0

    runBackgroundableTask("Resolving external annotations", project) { indicator ->
      indicator.isIndeterminate = false
      toProcess.forEach { mavenArtifact, library ->
        if (indicator.isCanceled) {
          return@forEach
        }
        count++
        indicator.fraction = (count.toDouble() + 1) / totalSize
        indicator.text = "Looking for annotations for '${mavenArtifact.libraryName}'"
        val mavenId = "${mavenArtifact.groupId}:${mavenArtifact.artifactId}:${mavenArtifact.version}"
        resolver.resolve(project, library, mavenId)
      }
    }
  }
  companion object {
    val LOG = Logger.getInstance(ExternalAnnotationsImporter::class.java)
  }
}
