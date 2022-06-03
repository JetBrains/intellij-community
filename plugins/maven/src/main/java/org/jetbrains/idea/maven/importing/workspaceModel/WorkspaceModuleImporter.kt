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
import org.jetbrains.idea.maven.project.MavenImportingSettings.GeneratedSourcesFolder
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File

class WorkspaceModuleImporter(
  private val project: Project,
  private val importData: MavenTreeModuleImportData,
  private val virtualFileUrlManager: VirtualFileUrlManager,
  private val builder: MutableEntityStorage,
  private val importingSettings: MavenImportingSettings,
  private val importFoldersByMavenIdCache: MutableMap<String, MavenImportFolderHolder>
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
                                    importFoldersByMavenIdCache: MutableMap<String, MavenImportFolderHolder>) {
    val folderImporter = WorkspaceFolderImporter(builder, virtualFileUrlManager, importingSettings)

    val importFolderHolder = importFoldersByMavenIdCache.getOrPut(importData.mavenProject.mavenId.key) { collectMavenFolders(importData) }
    when (importData.moduleData.type) {
      MavenModuleType.MAIN -> configMain(moduleEntity, importData, importFolderHolder, folderImporter)
      MavenModuleType.TEST -> configTest(moduleEntity, importData, importFolderHolder, folderImporter)
      MavenModuleType.AGGREGATOR_MAIN_TEST -> configMainAndTestAggregator(moduleEntity, importData, importFolderHolder, folderImporter)
      MavenModuleType.AGGREGATOR -> configAggregator(moduleEntity, importData, importFolderHolder, folderImporter)
      else -> config(moduleEntity, importData, importFolderHolder, folderImporter)
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


  private fun config(moduleEntity: ModuleEntity,
                     importData: MavenModuleImportData,
                     importFolderHolder: MavenImportFolderHolder,
                     folderImporter: WorkspaceFolderImporter) {
    importJavaSettings(moduleEntity, importData, importFolderHolder)

    folderImporter
      .createContentRoots(moduleEntity, importData,
                          importFolderHolder.excludedFoldersOfNoSourceSubFolder, importFolderHolder.doNotRegisterSourcesUnder,
                          importFolderHolder.generatedFoldersHolder)
  }

  private fun configAggregator(moduleEntity: ModuleEntity,
                               importData: MavenModuleImportData,
                               importFolderHolder: MavenImportFolderHolder,
                               folderImporter: WorkspaceFolderImporter) {
    importJavaSettingsAggregator(moduleEntity, importData)

    folderImporter
      .createContentRoots(moduleEntity, importData,
                          importFolderHolder.excludedFoldersOfNoSourceSubFolder, importFolderHolder.doNotRegisterSourcesUnder,
                          importFolderHolder.generatedFoldersHolder)
  }

  private fun configMainAndTestAggregator(moduleEntity: ModuleEntity,
                                          importData: MavenModuleImportData,
                                          importFolderHolder: MavenImportFolderHolder,
                                          folderImporter: WorkspaceFolderImporter) {
    importJavaSettingsMainAndTestAggregator(moduleEntity, importData)

    folderImporter
      .createContentRoots(moduleEntity, importData,
                          importFolderHolder.excludedFoldersOfNoSourceSubFolder, importFolderHolder.doNotRegisterSourcesUnder,
                          null)
  }

  private fun configMain(moduleEntity: ModuleEntity,
                         importData: MavenModuleImportData,
                         importFolderHolder: MavenImportFolderHolder,
                         folderImporter: WorkspaceFolderImporter) {
    importJavaSettingsMain(moduleEntity, importData, importFolderHolder)

    folderImporter
      .createContentRoots(moduleEntity, importData,
                          importFolderHolder.excludedFoldersOfNoSourceSubFolder, importFolderHolder.doNotRegisterSourcesUnder,
                          importFolderHolder.generatedFoldersHolder.toMain())
  }

  private fun configTest(moduleEntity: ModuleEntity,
                         importData: MavenModuleImportData,
                         importFolderHolder: MavenImportFolderHolder,
                         folderImporter: WorkspaceFolderImporter) {
    importJavaSettingsTest(moduleEntity, importData, importFolderHolder)

    folderImporter
      .createContentRoots(moduleEntity, importData,
                          importFolderHolder.excludedFoldersOfNoSourceSubFolder, importFolderHolder.doNotRegisterSourcesUnder,
                          importFolderHolder.generatedFoldersHolder.toTest())
  }

  private fun importJavaSettings(moduleEntity: ModuleEntity,
                                 importData: MavenModuleImportData,
                                 importFolderHolder: MavenImportFolderHolder) {
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
                                     importFolderHolder: MavenImportFolderHolder) {
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
                                     importFolderHolder: MavenImportFolderHolder) {
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

  private fun collectMavenFolders(importData: MavenModuleImportData): MavenImportFolderHolder { // extract
    val mavenProject = importData.mavenProject
    val outputPath = toAbsolutePath(mavenProject, mavenProject.outputDirectory)
    val testOutputPath = toAbsolutePath(mavenProject, mavenProject.testOutputDirectory)
    val targetDirPath = toAbsolutePath(mavenProject, mavenProject.buildDirectory)

    val excludedFoldersOfNoSourceSubFolder = mutableListOf<String>()
    if (importingSettings.isExcludeTargetFolder) {
      excludedFoldersOfNoSourceSubFolder.add(targetDirPath)
    }
    if (!FileUtil.isAncestor(targetDirPath, outputPath, false)) {
      excludedFoldersOfNoSourceSubFolder.add(outputPath)
    }
    if (!FileUtil.isAncestor(targetDirPath, testOutputPath, false)) {
      excludedFoldersOfNoSourceSubFolder.add(testOutputPath)
    }

    val doNotRegisterSourcesUnder = mutableListOf<String>()
    for (each in importData.mavenProject.suitableImporters) {
      each.collectExcludedFolders(importData.mavenProject, doNotRegisterSourcesUnder)
    }

    var annotationProcessorDirectory: String? = null
    var annotationProcessorTestDirectory: String? = null
    var generatedSourceFolder: String? = null
    var generatedTestSourceFolder: String? = null
    if (importingSettings.generatedSourcesFolder != GeneratedSourcesFolder.IGNORE) {
      annotationProcessorDirectory = mavenProject.getAnnotationProcessorDirectory(false)
      annotationProcessorTestDirectory = mavenProject.getAnnotationProcessorDirectory(true)
      if (File(annotationProcessorDirectory).list().isNullOrEmpty()) annotationProcessorDirectory = null
      if (File(annotationProcessorTestDirectory).list().isNullOrEmpty()) annotationProcessorTestDirectory = null
    }

    val generatedDir = mavenProject.getGeneratedSourcesDirectory(false)
    val generatedDirTest = mavenProject.getGeneratedSourcesDirectory(true)
    val targetChildren = File(targetDirPath).listFiles()
    if (targetChildren != null) {
      for (f in targetChildren) {
        if (!f.isDirectory) continue
        if (FileUtil.pathsEqual(generatedDir, f.path)) {
          generatedSourceFolder = toAbsolutePath(mavenProject, generatedDir)
        }
        else if (FileUtil.pathsEqual(generatedDirTest, f.path)) {
          generatedTestSourceFolder = toAbsolutePath(mavenProject, generatedDirTest)
        }
      }
    }
    val generatedFoldersHolder = GeneratedFoldersHolder(annotationProcessorDirectory, annotationProcessorTestDirectory,
                                                        generatedSourceFolder, generatedTestSourceFolder)

    return MavenImportFolderHolder(outputPath, testOutputPath, targetDirPath, excludedFoldersOfNoSourceSubFolder, doNotRegisterSourcesUnder,
                                   generatedFoldersHolder)
  }

  private fun toAbsolutePath(mavenProject: MavenProject, path: String) = MavenUtil.toPath(mavenProject, path).path

  class MavenImportFolderHolder(
    val outputPath: String,
    val testOutputPath: String,
    val targetDirPath: String,
    val excludedFoldersOfNoSourceSubFolder: List<String>,
    val doNotRegisterSourcesUnder: List<String>,
    val generatedFoldersHolder: GeneratedFoldersHolder
  )

  companion object {
    internal val JAVADOC_TYPE: LibraryRootTypeId = LibraryRootTypeId("JAVADOC")

    val EXTERNAL_SOURCE_ID get() = ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID

    fun linkedProjectPath(mavenProject: MavenProject): String {
      return FileUtil.toSystemIndependentName(mavenProject.directory)
    }
  }
}