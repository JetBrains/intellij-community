// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.codeInsight.ExternalAnnotationsArtifactsResolver
import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocation
import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocationSearcher
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.tasks.TasksBundle

@ApiStatus.Internal
private class MavenExternalAnnotationsConfigurator : MavenImporter("org.apache.maven.plugins", "maven-compiler-plugin"),
                                             MavenWorkspaceConfigurator {

  private val myProcessedLibraries = hashSetOf<MavenArtifact>()

  override fun processChangedModulesOnly(): Boolean = false

  override fun isMigratedToConfigurator(): Boolean = true

  override fun afterModelApplied(context: MavenWorkspaceConfigurator.AppliedModelContext) {
    if (!shouldRun(context.project)) return
    val projectsWithChanges = context.mavenProjectsWithModules.filter { it.hasChanges() }.map { it.mavenProject }

    if (projectsWithChanges.none()) return

    val libraryNameMap = context.importedEntities(LibraryEntity::class.java)
      .mapNotNull { it.findLibraryBridge(context.storage) }
      .associateBy { it.name }

    doConfigure(context.project, projectsWithChanges) { libraryName -> libraryNameMap[libraryName] }
  }

  override fun postProcess(module: Module,
                           mavenProject: MavenProject,
                           changes: MavenProjectChanges,
                           modifiableModelsProvider: IdeModifiableModelsProvider) {
    if (!shouldRun(module.project)) return
    doConfigure(module.project, sequenceOf(mavenProject)) { libraryName ->
      modifiableModelsProvider.getLibraryByName(libraryName)
    }
  }

  private fun shouldRun(project: Project): Boolean {
    if (!shouldImportExternalAnnotations(project)) {
      return false
    }

    val resolvers = getResolvers()
    if (resolvers.isEmpty()) {
      return false
    }

    return true
  }

  private fun getResolvers(): List<ExternalAnnotationsArtifactsResolver> = ExternalAnnotationsArtifactsResolver.EP_NAME.extensionList

  private fun doConfigure(project: Project, mavenProjects: Sequence<MavenProject>, libraryFinder: (libraryName: String) -> Library?) {
    val toProcess = mutableMapOf<MavenArtifact, Library>()

    mavenProjects.forEach { eachMavenProject ->
      eachMavenProject.dependencies.asSequence()
        .filter { !myProcessedLibraries.contains(it) }
        .forEach {
          val library = libraryFinder.invoke(it.libraryName)
          if (library != null) {
            toProcess[it] = library
            myProcessedLibraries.add(it)
          }
        }
    }

    if (toProcess.isEmpty()) {
      return
    }

    val totalSize = toProcess.size
    var count = 0

    val locationsToSkip = mutableSetOf<AnnotationsLocation>()
    runBackgroundableTask(TasksBundle.message("maven.tasks.external.annotations.resolving.title"), project) { indicator ->
      val resolvers = getResolvers()

      indicator.isIndeterminate = false
      toProcess.forEach { (mavenArtifact, library) ->
        if (indicator.isCanceled) {
          return@forEach
        }
        indicator.text = TasksBundle.message("maven.tasks.external.annotations.looking.for", mavenArtifact.libraryName)
        val locations = AnnotationsLocationSearcher.findAnnotationsLocation(project, library, mavenArtifact.artifactId,
                                                                            mavenArtifact.groupId, mavenArtifact.version)

        locations.forEach locations@{ location ->
          if (locationsToSkip.contains(location)) return@locations
          if (!resolvers.fold(false) { acc, res -> acc || res.resolve(project, library, location) } ) {
            locationsToSkip.add(location)
          }
        }

        indicator.fraction = (++count).toDouble() / totalSize
      }
    }
  }

  private fun shouldImportExternalAnnotations(project: Project) =
    MavenProjectsManager.getInstance(project).run {
      importingSettings.isDownloadAnnotationsAutomatically && !generalSettings.isWorkOffline
    }

  companion object {
    val LOG = Logger.getInstance(MavenExternalAnnotationsConfigurator::class.java)
  }
}
