// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.workspaceModel.ide.impl.JpsEntitySourceFactory
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.addJavaModuleSettingsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addLibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenModelUtil
import org.jetbrains.idea.maven.importing.tree.MavenModuleImportData
import org.jetbrains.idea.maven.importing.tree.MavenModuleType
import org.jetbrains.idea.maven.importing.tree.MavenTreeModuleImportData
import org.jetbrains.idea.maven.importing.tree.dependency.*
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenProject

class WorkspaceModuleImporter(
  private val project: Project,
  private val importData: MavenTreeModuleImportData,
  private val virtualFileUrlManager: VirtualFileUrlManager,
  private val builder: MutableEntityStorage,
  private val importingSettings: MavenImportingSettings,
  private val importFoldersByMavenIdCache: MutableMap<String, WorkspaceFolderImporter.CachedProjectFolders>
) {
  private val externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(EXTERNAL_SOURCE_ID)

  fun importModule(): ModuleEntity {
    val baseModuleDirPath = importingSettings.dedicatedModuleDir.ifBlank { null } ?: importData.mavenProject.directory
    val baseModuleDir = virtualFileUrlManager.fromPath(baseModuleDirPath)
    val entitySource = JpsEntitySourceFactory.createEntitySourceForModule(project, baseModuleDir, externalSource)

    val moduleName = importData.moduleData.moduleName

    val dependencies = collectDependencies(moduleName, importData.dependencies, entitySource)
    val moduleEntity = createModuleEntity(moduleName, importData.mavenProject, dependencies, entitySource)
    configureModuleEntity(importData, moduleEntity, importFoldersByMavenIdCache)
    return moduleEntity
  }

  private fun createModuleEntity(moduleName: String,
                                 mavenProject: MavenProject,
                                 dependencies: List<ModuleDependencyItem>,
                                 entitySource: EntitySource): ModuleEntity {
    val moduleEntity = builder.addModuleEntity(moduleName, dependencies, entitySource, ModuleTypeId.JAVA_MODULE)
    val externalSystemModuleOptionsEntity = ExternalSystemModuleOptionsEntity(entitySource) {
      module = moduleEntity
      externalSystem = EXTERNAL_SOURCE_ID
      linkedProjectPath = linkedProjectPath(mavenProject)
    }
    builder.addEntity(externalSystemModuleOptionsEntity)
    return moduleEntity

  }

  private fun configureModuleEntity(importData: MavenModuleImportData,
                                    moduleEntity: ModuleEntity,
                                    importFoldersByMavenIdCache: MutableMap<String, WorkspaceFolderImporter.CachedProjectFolders>) {
    val folderImporter = WorkspaceFolderImporter(builder, virtualFileUrlManager, importingSettings, importFoldersByMavenIdCache)

    val importFolderHolder = folderImporter.createContentRoots(moduleEntity, importData)
    when (importData.moduleData.type) {
      MavenModuleType.MAIN -> importJavaSettingsMain(moduleEntity, importData, importFolderHolder)
      MavenModuleType.TEST -> importJavaSettingsTest(moduleEntity, importData, importFolderHolder)
      MavenModuleType.AGGREGATOR_MAIN_TEST -> importJavaSettingsMainAndTestAggregator(moduleEntity, importData)
      MavenModuleType.AGGREGATOR -> importJavaSettingsAggregator(moduleEntity, importData)
      else -> importJavaSettings(moduleEntity, importData, importFolderHolder)
    }
  }

  private fun collectDependencies(moduleName: String,
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


  private fun importJavaSettings(moduleEntity: ModuleEntity,
                                 importData: MavenModuleImportData,
                                 importFolderHolder: WorkspaceFolderImporter.CachedProjectFolders) {
    val languageLevel = MavenModelUtil.getLanguageLevel(importData.mavenProject) { importData.moduleData.sourceLanguageLevel }
    val inheritCompilerOutput: Boolean
    val compilerOutputUrl: VirtualFileUrl?
    val compilerOutputUrlForTests: VirtualFileUrl?
    if (importingSettings.isUseMavenOutput) {
      inheritCompilerOutput = false
      compilerOutputUrl = virtualFileUrlManager.fromPath(importFolderHolder.outputPath)
      compilerOutputUrlForTests = virtualFileUrlManager.fromPath(importFolderHolder.testOutputPath)
    }
    else {
      inheritCompilerOutput = true
      compilerOutputUrl = null
      compilerOutputUrlForTests = null
    }
    builder.addJavaModuleSettingsEntity(inheritCompilerOutput, false, compilerOutputUrl, compilerOutputUrlForTests,
                                        languageLevel.name, moduleEntity, moduleEntity.entitySource)
  }

  private fun importJavaSettingsMainAndTestAggregator(moduleEntity: ModuleEntity, importData: MavenModuleImportData) {
    val languageLevel = MavenModelUtil.getLanguageLevel(importData.mavenProject) { importData.moduleData.sourceLanguageLevel }
    builder.addJavaModuleSettingsEntity(true, false, null, null, languageLevel.name, moduleEntity, moduleEntity.entitySource)
  }

  private fun importJavaSettingsAggregator(moduleEntity: ModuleEntity, importData: MavenModuleImportData) {
    val languageLevel = MavenModelUtil.getLanguageLevel(importData.mavenProject) { importData.moduleData.sourceLanguageLevel }
    builder.addJavaModuleSettingsEntity(true, false, null, null, languageLevel.name, moduleEntity, moduleEntity.entitySource)
  }

  private fun importJavaSettingsMain(moduleEntity: ModuleEntity,
                                     importData: MavenModuleImportData,
                                     importFolderHolder: WorkspaceFolderImporter.CachedProjectFolders) {
    val languageLevel = MavenModelUtil.getLanguageLevel(importData.mavenProject) { importData.moduleData.sourceLanguageLevel }
    val inheritCompilerOutput: Boolean
    val compilerOutputUrl: VirtualFileUrl?
    if (importingSettings.isUseMavenOutput) {
      inheritCompilerOutput = false
      compilerOutputUrl = virtualFileUrlManager.fromPath(importFolderHolder.outputPath)
    }
    else {
      inheritCompilerOutput = true
      compilerOutputUrl = null
    }
    builder.addJavaModuleSettingsEntity(inheritCompilerOutput, false, compilerOutputUrl, null,
                                        languageLevel.name, moduleEntity, moduleEntity.entitySource)
  }

  private fun importJavaSettingsTest(moduleEntity: ModuleEntity,
                                     importData: MavenModuleImportData,
                                     importFolderHolder: WorkspaceFolderImporter.CachedProjectFolders) {
    val languageLevel = MavenModelUtil.getLanguageLevel(importData.mavenProject) { importData.moduleData.sourceLanguageLevel }
    val inheritCompilerOutput: Boolean
    val compilerOutputUrlForTests: VirtualFileUrl?
    if (importingSettings.isUseMavenOutput) {
      inheritCompilerOutput = false
      compilerOutputUrlForTests = virtualFileUrlManager.fromPath(importFolderHolder.testOutputPath)
    }
    else {
      inheritCompilerOutput = true
      compilerOutputUrlForTests = null
    }
    builder.addJavaModuleSettingsEntity(inheritCompilerOutput, false, null, compilerOutputUrlForTests,
                                        languageLevel.name, moduleEntity, moduleEntity.entitySource)
  }



  companion object {
    internal val JAVADOC_TYPE: LibraryRootTypeId = LibraryRootTypeId("JAVADOC")

    val EXTERNAL_SOURCE_ID get() = ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID

    fun linkedProjectPath(mavenProject: MavenProject): String {
      return FileUtil.toSystemIndependentName(mavenProject.directory)
    }
  }
}