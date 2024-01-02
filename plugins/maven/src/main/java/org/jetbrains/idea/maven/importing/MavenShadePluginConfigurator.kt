// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.LegacyBridgeJpsEntitySourceFactory
import org.jetbrains.idea.maven.importing.workspaceModel.WorkspaceModuleImporter
import org.jetbrains.idea.maven.project.MavenProject
import java.nio.file.Path
import kotlin.io.path.pathString

class MavenShadePluginConfigurator : MavenWorkspaceConfigurator {
  private val externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(WorkspaceModuleImporter.EXTERNAL_SOURCE_ID)

  override fun beforeModelApplied(context: MavenWorkspaceConfigurator.MutableModelContext) {
    // find all poms with maven-shade-plugin
    val shadeProjectsWithModules = context.mavenProjectsWithModules.filter {
      it.mavenProject.findPlugin("org.apache.maven.plugins", "maven-shade-plugin") != null
    }.toList()

    if (shadeProjectsWithModules.isEmpty()) return

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
          addJarDependency(context.storage, context.project, module, shadeModuleIdToMavenProject[dependencyModuleId]!!)
        }
      }
    }
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

    // TODO: fileInDirectoryNames?
    val librarySource = LegacyBridgeJpsEntitySourceFactory.createEntitySourceForProjectLibrary(project, externalSource)

    builder addEntity LibraryEntity(libraryId.name, libraryId.tableId, libraryRootsProvider(), librarySource)
  }
}