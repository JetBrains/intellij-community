// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.project.*
import org.jetbrains.idea.maven.tasks.TasksBundle

class ExternalAnnotationsImporter : MavenImporter("org.apache.maven.plugins", "maven-compiler-plugin") {

  private val myProcessedLibraries = hashSetOf<MavenArtifact>()

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
    //do nothing
  }

  override fun postProcess(module: Module,
                           mavenProject: MavenProject,
                           changes: MavenProjectChanges,
                           modifiableModelsProvider: IdeModifiableModelsProvider) {

    val resolvers = ExternalAnnotationsArtifactsResolver.EP_NAME.extensionList
    if (resolvers.isEmpty()) {
      return
    }

    val project = module.project
    val librariesMap = mutableMapOf<MavenArtifact, Library>()

    if (!shouldImportExternalAnnotations(project)) {
      return
    }

    mavenProject.dependencies.forEach {
      val library = modifiableModelsProvider.getLibraryByName(it.libraryName)
      if (library != null) {
        librariesMap[it] = library
      }
    }

    val toProcess = librariesMap.filterKeys { myProcessedLibraries.add(it) }
    if (toProcess.isEmpty()) {
      return
    }

    val totalSize = toProcess.size
    var count = 0

    val locationsToSkip = mutableSetOf<AnnotationsLocation>();
    runBackgroundableTask(TasksBundle.message("maven.tasks.external.annotations.resolving.title"), project) { indicator ->
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
    val LOG = Logger.getInstance(ExternalAnnotationsImporter::class.java)
  }
}
