// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.rawProgressReporter
import com.intellij.platform.util.progress.withRawProgressReporter
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.LegacyBridgeJpsEntitySourceFactory
import org.jetbrains.idea.maven.buildtool.MavenEventHandler
import org.jetbrains.idea.maven.importing.workspaceModel.WorkspaceModuleImporter
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.project.MavenEmbeddersManager
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenGoalExecutionRequest
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString

internal class MavenShadePluginConfigurator : MavenWorkspaceConfigurator {
  private val externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(WorkspaceModuleImporter.EXTERNAL_SOURCE_ID)

  override fun beforeModelApplied(context: MavenWorkspaceConfigurator.MutableModelContext) {
    if (!Registry.`is`("maven.shade.plugin.create.uber.jar.dependency")) return

    // find all poms with maven-shade-plugin
    val shadeProjectsWithModules = context.mavenProjectsWithModules.filter {
      it.mavenProject.findPlugin("org.apache.maven.plugins", "maven-shade-plugin") != null
    }.toList()

    if (shadeProjectsWithModules.isEmpty()) return

    val shadedMavenProjectsToBuildUberJar = mutableSetOf<MavenProject>()

    val shadeModuleIdToMavenProject = HashMap<ModuleId, MavenProject>()
    for (shadeProjectWithModules in shadeProjectsWithModules) {
      for (module in shadeProjectWithModules.modules) {
        shadeModuleIdToMavenProject[module.module.symbolicId] = shadeProjectWithModules.mavenProject
      }
    }
    val shadeModuleIds = shadeModuleIdToMavenProject.keys

    // process dependent modules
    for (module in context.storage.entities(ModuleEntity::class.java)) {
      for (dependency in module.dependencies.filterIsInstance<ModuleDependencyItem.Exportable.ModuleDependency>()) {
        val dependencyModuleId = dependency.module
        if (shadeModuleIds.contains(dependencyModuleId)) {
          val mavenProject = shadeModuleIdToMavenProject[dependencyModuleId]!!
          addJarDependency(context.storage, context.project, module, mavenProject)
          shadedMavenProjectsToBuildUberJar.add(mavenProject)
        }
      }
    }

    context.putUserDataIfAbsent(SHADED_MAVEN_PROJECTS, shadedMavenProjectsToBuildUberJar)
  }

  private fun addJarDependency(builder: MutableEntityStorage,
                               project: Project,
                               module: ModuleEntity,
                               dependencyMavenProject: MavenProject) {
    val libraryName = "Maven Shade: ${dependencyMavenProject.mavenId.displayString}"
    val libraryId = LibraryId(libraryName, LibraryTableId.ProjectLibraryTableId)

    val mavenId = dependencyMavenProject.mavenId
    val fileName = "${mavenId.artifactId}-${mavenId.version}.jar"
    val jarPath = Path.of(dependencyMavenProject.buildDirectory, fileName).pathString
    val jarUrl = VirtualFileUrlManager.getInstance(project).fromUrl("jar://$jarPath!/")

    addLibraryEntity(
      builder,
      project,
      libraryId
    ) {
      listOf(LibraryRoot(jarUrl, LibraryRootTypeId.COMPILED))
    }

    val scope = ModuleDependencyItem.DependencyScope.COMPILE
    val libraryDependency = ModuleDependencyItem.Exportable.LibraryDependency(libraryId, false, scope)

    builder.modifyEntity(module) {
      this.dependencies.add(libraryDependency)
    }
  }

  private fun addLibraryEntity(
    builder: MutableEntityStorage,
    project: Project,
    libraryId: LibraryId,
    // lazy provider to avoid roots creation for already added libraries
    libraryRootsProvider: () -> List<LibraryRoot>) {
    if (libraryId in builder) return

    val librarySource = LegacyBridgeJpsEntitySourceFactory.createEntitySourceForProjectLibrary(project, externalSource)

    builder addEntity LibraryEntity(libraryId.name, libraryId.tableId, libraryRootsProvider(), librarySource)
  }
}

private val SHADED_MAVEN_PROJECTS = Key.create<Set<MavenProject>>("SHADED_MAVEN_PROJECTS")

internal class MavenShadeFacetPostTaskConfigurator : MavenAfterImportConfigurator {
  override fun afterImport(context: MavenAfterImportConfigurator.Context) {
    if (!Registry.`is`("maven.shade.plugin.generate.uber.jar")) return

    val shadedMavenProjects = context.getUserData(SHADED_MAVEN_PROJECTS)
    if (shadedMavenProjects.isNullOrEmpty()) return

    val project = context.project
    val projectsManager = MavenProjectsManager.getInstance(project)

    val baseDirsToMavenProjects = MavenUtil.groupByBasedir(shadedMavenProjects, projectsManager.projectsTree)

    val embeddersManager = projectsManager.embeddersManager
    val syncConsole = projectsManager.syncConsole

    for (baseDir in baseDirsToMavenProjects.keySet()) {
      packageJarsForBaseDir(project, embeddersManager, syncConsole, baseDirsToMavenProjects[baseDir], baseDir)
    }

    val filesToRefresh = shadedMavenProjects.map { Path.of(it.buildDirectory) }
    LocalFileSystem.getInstance().refreshNioFiles(filesToRefresh, true, false, null)
  }

  private fun packageJarsForBaseDir(project: Project,
                                    embeddersManager: MavenEmbeddersManager,
                                    mavenEventHandler: MavenEventHandler,
                                    mavenProjects: Collection<MavenProject>,
                                    baseDir: String) {
    val embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_POST_PROCESSING, baseDir)

    val requests = mavenProjects.map { MavenGoalExecutionRequest(File(it.path), MavenExplicitProfiles.NONE) }.toList()

    val names = mavenProjects.map { it.displayName }
    val text = StringUtil.shortenPathWithEllipsis(StringUtil.join(names, ", "), 200)

    runBlockingMaybeCancellable {
      withBackgroundProgress(project, MavenProjectBundle.message("maven.generating.uber.jars", text), true) {
        withRawProgressReporter {
          embedder.executeGoal(requests, "package", rawProgressReporter!!, mavenEventHandler)
        }
      }
    }
  }
}