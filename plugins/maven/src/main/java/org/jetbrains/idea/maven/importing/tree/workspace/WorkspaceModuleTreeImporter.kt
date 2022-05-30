// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.tree.workspace

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.workspaceModel.ide.impl.JpsEntitySourceFactory
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.addJavaModuleSettingsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addLibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenModelUtil
import org.jetbrains.idea.maven.importing.tree.MavenTreeModuleImportData
import org.jetbrains.idea.maven.importing.tree.dependency.BaseDependency
import org.jetbrains.idea.maven.importing.tree.dependency.LibraryDependency
import org.jetbrains.idea.maven.importing.tree.dependency.ModuleDependency
import org.jetbrains.idea.maven.importing.tree.dependency.SystemDependency
import org.jetbrains.idea.maven.importing.workspaceModel.WorkspaceModuleImporterBase
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenImportingSettings

class WorkspaceModuleTreeImporter(
  private val importData: MavenTreeModuleImportData,
  virtualFileUrlManager: VirtualFileUrlManager,
  builder: MutableEntityStorage,
  importingSettings: MavenImportingSettings,
  private val importFoldersByMavenIdCache: MutableMap<String, MavenImportFolderHolder>,
  private val project: Project) : WorkspaceModuleImporterBase(virtualFileUrlManager, builder, importingSettings) {

  fun importModule(): ModuleEntity {
    val baseModuleDirPath = importingSettings.dedicatedModuleDir.ifBlank { null } ?: importData.mavenProject.directory
    val baseModuleDir = virtualFileUrlManager.fromPath(baseModuleDirPath)
    val entitySource = JpsEntitySourceFactory.createEntitySourceForModule(project, baseModuleDir, externalSource)
    val dependencies = collectDependencies(importData, entitySource)

    return createModuleEntity(importData, dependencies, entitySource, importFoldersByMavenIdCache)
  }

  private fun collectDependencies(importData: MavenTreeModuleImportData, entitySource: EntitySource): List<ModuleDependencyItem> {
    val result = mutableListOf(ModuleDependencyItem.InheritedSdkDependency, ModuleDependencyItem.ModuleSourceDependency)
    for (dependency in importData.dependencies) {
      if (dependency is SystemDependency) {
        result.add(createSystemDependency(dependency.artifact, entitySource))
      }
      else if (dependency is LibraryDependency) {
        result.add(createLibraryDependency(dependency.artifact))
      }
      else if (dependency is ModuleDependency) {
        result.add(ModuleDependencyItem.Exportable
                     .ModuleDependency(ModuleId(dependency.artifact), false, toScope(dependency.scope), dependency.isTestJar))
      }
      else if (dependency is BaseDependency) {
        result.add(createLibraryDependency(dependency.artifact))
      }
    }
    return result
  }

  private fun toScope(scope: DependencyScope): ModuleDependencyItem.DependencyScope =
    when (scope) {
      DependencyScope.RUNTIME -> ModuleDependencyItem.DependencyScope.RUNTIME
      DependencyScope.TEST -> ModuleDependencyItem.DependencyScope.TEST
      DependencyScope.PROVIDED -> ModuleDependencyItem.DependencyScope.PROVIDED
      else -> ModuleDependencyItem.DependencyScope.COMPILE
    }


  private fun createSystemDependency(artifact: MavenArtifact,
                                     moduleEntitySource: EntitySource): ModuleDependencyItem.Exportable.LibraryDependency {
    assert(MavenConstants.SCOPE_SYSTEM == artifact.scope)
    val roots = ArrayList<LibraryRoot>()

    roots.add(LibraryRoot(
      virtualFileUrlManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, null, null)),
      LibraryRootTypeId.COMPILED)
    )

    val libraryTableId = LibraryTableId.ModuleLibraryTableId(
      moduleId = ModuleId(importData.mavenProject.displayName)) //(ModuleId(moduleEntity.name))

    builder.addLibraryEntity(artifact.libraryName, libraryTableId,
                             roots,
                             emptyList(), moduleEntitySource)

    return ModuleDependencyItem.Exportable.LibraryDependency(LibraryId(artifact.libraryName, libraryTableId), false,
                                                             artifact.dependencyScope)
  }

  private fun createLibraryDependency(artifact: MavenArtifact): ModuleDependencyItem.Exportable.LibraryDependency {
    assert(MavenConstants.SCOPE_SYSTEM != artifact.scope)
    val libraryId = LibraryId(artifact.libraryName, LibraryTableId.ProjectLibraryTableId)
    if (builder.resolve(libraryId) == null) {
      addLibraryToProjectTable(artifact)
    }

    return ModuleDependencyItem.Exportable.LibraryDependency(libraryId, false, artifact.dependencyScope)
  }

  private fun addLibraryToProjectTable(artifact: MavenArtifact): LibraryEntity {
    val roots = ArrayList<LibraryRoot>()

    roots.add(LibraryRoot(virtualFileUrlManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, null, null)),
                          LibraryRootTypeId.COMPILED))
    roots.add(
      LibraryRoot(virtualFileUrlManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, "javadoc", "jar")),
                  JAVADOC_TYPE))
    roots.add(
      LibraryRoot(virtualFileUrlManager.fromUrl(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, "sources", "jar")),
                  LibraryRootTypeId.SOURCES))

    val libraryTableId = LibraryTableId.ProjectLibraryTableId //(ModuleId(moduleEntity.name))

    return builder.addLibraryEntity(artifact.libraryName, libraryTableId,
                                    roots,
                                    emptyList(), JpsEntitySourceFactory.createEntitySourceForProjectLibrary(project, externalSource))
  }

  private val MavenArtifact.dependencyScope: ModuleDependencyItem.DependencyScope
    get() = when (scope) {
      MavenConstants.SCOPE_RUNTIME -> ModuleDependencyItem.DependencyScope.RUNTIME
      MavenConstants.SCOPE_TEST -> ModuleDependencyItem.DependencyScope.TEST
      MavenConstants.SCOPE_PROVIDED -> ModuleDependencyItem.DependencyScope.PROVIDED
      else -> ModuleDependencyItem.DependencyScope.COMPILE
    }
}