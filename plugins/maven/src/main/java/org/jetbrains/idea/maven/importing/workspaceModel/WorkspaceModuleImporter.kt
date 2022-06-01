// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.workspaceModel.ide.impl.JpsEntitySourceFactory
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.addLibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenModelUtil
import org.jetbrains.idea.maven.importing.tree.MavenTreeModuleImportData
import org.jetbrains.idea.maven.importing.tree.dependency.*
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenImportingSettings

class WorkspaceModuleImporter(
  importData: MavenTreeModuleImportData,
  virtualFileUrlManager: VirtualFileUrlManager,
  builder: MutableEntityStorage,
  importingSettings: MavenImportingSettings,
  importFoldersByMavenIdCache: MutableMap<String, MavenImportFolderHolder>,
  project: Project) : WorkspaceModuleImporterBase(importData, project, virtualFileUrlManager, builder, importingSettings,
                                                  importFoldersByMavenIdCache) {

  override fun collectDependencies(moduleName: String,
                                   dependencies: List<Any>,
                                   moduleEntitySource: EntitySource): List<ModuleDependencyItem> {
    val result = mutableListOf(ModuleDependencyItem.InheritedSdkDependency, ModuleDependencyItem.ModuleSourceDependency)
    for (dependency in dependencies) {
      if (dependency is SystemDependency) {
        result.add(createSystemDependency(moduleName, dependency.artifact, moduleEntitySource))
      }
      else if (dependency is LibraryDependency) {
        result.add(createLibraryDependency(dependency.artifact))
      }
      else if (dependency is AttachedJarDependency) {
        result.add(createLibraryDependency(
          dependency.artifact,
          toScope(dependency.scope),
          classUrls = dependency.classes.map(::pathToUrl),
          sourceUrls = dependency.sources.map(::pathToUrl),
          javadocUrls = dependency.javadocs.map(::pathToUrl),
        ))
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

  private fun pathToUrl(it: String) = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, it) + JarFileSystem.JAR_SEPARATOR

  private fun toScope(scope: DependencyScope): ModuleDependencyItem.DependencyScope =
    when (scope) {
      DependencyScope.RUNTIME -> ModuleDependencyItem.DependencyScope.RUNTIME
      DependencyScope.TEST -> ModuleDependencyItem.DependencyScope.TEST
      DependencyScope.PROVIDED -> ModuleDependencyItem.DependencyScope.PROVIDED
      else -> ModuleDependencyItem.DependencyScope.COMPILE
    }


  private fun createSystemDependency(moduleName: String,
                                     artifact: MavenArtifact,
                                     moduleEntitySource: EntitySource): ModuleDependencyItem.Exportable.LibraryDependency {
    assert(MavenConstants.SCOPE_SYSTEM == artifact.scope)

    val libraryId = LibraryId(artifact.libraryName, LibraryTableId.ModuleLibraryTableId(moduleId = ModuleId(moduleName)))
    addLibraryEntity(libraryId,
                     classUrls = listOf(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, null, null)),
                     sourceUrls = emptyList(),
                     javadocUrls = emptyList(),
                     moduleEntitySource)
    return ModuleDependencyItem.Exportable.LibraryDependency(libraryId, false, artifact.dependencyScope)
  }

  private fun createLibraryDependency(artifact: MavenArtifact): ModuleDependencyItem.Exportable.LibraryDependency {
    assert(MavenConstants.SCOPE_SYSTEM != artifact.scope)
    return createLibraryDependency(artifact.libraryName,
                                   artifact.dependencyScope,
                                   classUrls = listOf(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, null, null)),
                                   sourceUrls = listOf(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, "sources", "jar")),
                                   javadocUrls = listOf(MavenModelUtil.getArtifactUrlForClassifierAndExtension(artifact, "javadoc", "jar")))
  }

  private fun createLibraryDependency(
    libraryName: String,
    scope: ModuleDependencyItem.DependencyScope,
    classUrls: List<String>,
    sourceUrls: List<String>,
    javadocUrls: List<String>,
    source: EntitySource = JpsEntitySourceFactory.createEntitySourceForProjectLibrary(project, externalSource)
  ): ModuleDependencyItem.Exportable.LibraryDependency {
    val libraryId = LibraryId(libraryName, LibraryTableId.ProjectLibraryTableId)

    addLibraryEntity(libraryId, classUrls, sourceUrls, javadocUrls, source)

    return ModuleDependencyItem.Exportable.LibraryDependency(libraryId, false, scope)
  }

  private fun addLibraryEntity(
    libraryId: LibraryId,
    classUrls: List<String>,
    sourceUrls: List<String>,
    javadocUrls: List<String>,
    source: EntitySource) {
    if (builder.resolve(libraryId) != null) return;

    val roots = mutableListOf<LibraryRoot>()

    roots.addAll(classUrls.map { LibraryRoot(virtualFileUrlManager.fromUrl(it), LibraryRootTypeId.COMPILED) })
    roots.addAll(sourceUrls.map { LibraryRoot(virtualFileUrlManager.fromUrl(it), LibraryRootTypeId.SOURCES) })
    roots.addAll(javadocUrls.map { LibraryRoot(virtualFileUrlManager.fromUrl(it), JAVADOC_TYPE) })

    builder.addLibraryEntity(libraryId.name,
                             libraryId.tableId,
                             roots,
                             emptyList(),
                             source)
  }

  private val MavenArtifact.dependencyScope: ModuleDependencyItem.DependencyScope
    get() = when (scope) {
      MavenConstants.SCOPE_RUNTIME -> ModuleDependencyItem.DependencyScope.RUNTIME
      MavenConstants.SCOPE_TEST -> ModuleDependencyItem.DependencyScope.TEST
      MavenConstants.SCOPE_PROVIDED -> ModuleDependencyItem.DependencyScope.PROVIDED
      else -> ModuleDependencyItem.DependencyScope.COMPILE
    }
}