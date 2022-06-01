// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspaceModel.ide.impl.JpsEntitySourceFactory
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.addJavaModuleSettingsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ExternalSystemModuleOptionsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryRootTypeId
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleDependencyItem
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenModelUtil
import org.jetbrains.idea.maven.importing.tree.MavenModuleImportData
import org.jetbrains.idea.maven.importing.tree.MavenModuleType
import org.jetbrains.idea.maven.importing.tree.MavenTreeModuleImportData
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenImportingSettings.GeneratedSourcesFolder
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File

abstract class WorkspaceModuleImporterBase(
  private val importData: MavenTreeModuleImportData,
  protected val project: Project,
  protected val virtualFileUrlManager: VirtualFileUrlManager,
  protected val builder: MutableEntityStorage,
  protected val importingSettings: MavenImportingSettings,
  private val importFoldersByMavenIdCache: MutableMap<String, MavenImportFolderHolder>
) {
  protected val externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(EXTERNAL_SOURCE_ID)

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

  protected abstract fun collectDependencies(moduleName: String,
                                             dependencies: List<Any>,
                                             moduleEntitySource: EntitySource): List<ModuleDependencyItem>

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

  private fun config(moduleEntity: ModuleEntity,
                     importData: MavenModuleImportData,
                     importFolderHolder: MavenImportFolderHolder,
                     folderImporter: WorkspaceFolderImporter) {
    importJavaSettings(moduleEntity, importData, importFolderHolder)

    folderImporter
      .createContentRoots(moduleEntity, importData, importFolderHolder.excludedFolders, importFolderHolder.generatedFoldersHolder)
  }

  private fun configAggregator(moduleEntity: ModuleEntity,
                               importData: MavenModuleImportData,
                               importFolderHolder: MavenImportFolderHolder,
                               folderImporter: WorkspaceFolderImporter) {
    importJavaSettingsAggregator(moduleEntity, importData)

    folderImporter
      .createContentRoots(moduleEntity, importData, importFolderHolder.excludedFolders, importFolderHolder.generatedFoldersHolder, true)
  }

  private fun configMainAndTestAggregator(moduleEntity: ModuleEntity,
                                          importData: MavenModuleImportData,
                                          importFolderHolder: MavenImportFolderHolder,
                                          folderImporter: WorkspaceFolderImporter) {
    importJavaSettingsMainAndTestAggregator(moduleEntity, importData)

    folderImporter
      .createContentRoots(moduleEntity, importData, importFolderHolder.excludedFolders, null, true)
  }

  private fun configMain(moduleEntity: ModuleEntity,
                         importData: MavenModuleImportData,
                         importFolderHolder: MavenImportFolderHolder,
                         folderImporter: WorkspaceFolderImporter) {
    importJavaSettingsMain(moduleEntity, importData, importFolderHolder)

    folderImporter
      .createContentRoots(moduleEntity, importData, importFolderHolder.excludedFolders, importFolderHolder.generatedFoldersHolder.toMain())
  }

  private fun configTest(moduleEntity: ModuleEntity,
                         importData: MavenModuleImportData,
                         importFolderHolder: MavenImportFolderHolder,
                         folderImporter: WorkspaceFolderImporter) {
    importJavaSettingsTest(moduleEntity, importData, importFolderHolder)

    folderImporter
      .createContentRoots(moduleEntity, importData, importFolderHolder.excludedFolders, importFolderHolder.generatedFoldersHolder.toTest())
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

    val excludedFolders = mutableListOf<String>()
    if (importingSettings.isExcludeTargetFolder) {
      excludedFolders.add(targetDirPath)
    }
    if (!FileUtil.isAncestor(targetDirPath, outputPath, false)) {
      excludedFolders.add(outputPath)
    }
    if (!FileUtil.isAncestor(targetDirPath, testOutputPath, false)) {
      excludedFolders.add(testOutputPath)
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

    return MavenImportFolderHolder(outputPath, testOutputPath, targetDirPath, excludedFolders, generatedFoldersHolder)
  }

  private fun toAbsolutePath(mavenProject: MavenProject, path: String) = MavenUtil.toPath(mavenProject, path).path

  class MavenImportFolderHolder(
    val outputPath: String,
    val testOutputPath: String,
    val targetDirPath: String,
    val excludedFolders: List<String>,
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