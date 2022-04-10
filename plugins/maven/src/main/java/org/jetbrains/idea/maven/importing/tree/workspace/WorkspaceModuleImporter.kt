// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.tree.workspace

import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspaceModel.ide.impl.JpsEntitySourceFactory
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenModelUtil
import org.jetbrains.idea.maven.importing.tree.MavenModuleType
import org.jetbrains.idea.maven.importing.tree.dependency.BaseDependency
import org.jetbrains.idea.maven.importing.tree.dependency.LibraryDependency
import org.jetbrains.idea.maven.importing.tree.dependency.ModuleDependency
import org.jetbrains.idea.maven.importing.tree.dependency.SystemDependency
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenImportingSettings.GeneratedSourcesFolder
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File

class WorkspaceModuleImporter(
  private val importData: MavenModuleImportData,
  private val virtualFileUrlManager: VirtualFileUrlManager,
  private val builder: WorkspaceEntityStorageBuilder,
  private val importingSettings: MavenImportingSettings,
  private val mavenImportFoldersByMavenId: MutableMap<String, MavenImportFolderHolder>,
  private val project: Project) {

  private val externalSource = ExternalProjectSystemRegistry.getInstance().getSourceById(
    ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID)

  fun importModule(): ModuleEntity {
    val importFolderHolder = mavenImportFoldersByMavenId.getOrPut(importData.mavenProject.mavenId.key) { collectMavenFolders() }

    val baseModuleDirPath = importingSettings.dedicatedModuleDir.ifBlank { null } ?: importData.mavenProject.directory
    val baseModuleDir = virtualFileUrlManager.fromPath(baseModuleDirPath)
    val entitySource = JpsEntitySourceFactory.createEntitySourceForModule(project, baseModuleDir, externalSource)
    val dependencies = collectDependencies(importData, entitySource)
    val moduleName = importData.moduleData.moduleName
    val moduleEntity = builder.addModuleEntity(moduleName, dependencies, entitySource, ModuleTypeId.JAVA_MODULE)
    builder.addEntity(ModifiableExternalSystemModuleOptionsEntity::class.java, entitySource) {
      module = moduleEntity
      externalSystem = externalSource.id
    }
    val folderImporter = WorkspaceFolderImporter(builder, virtualFileUrlManager, importingSettings)

    when (importData.moduleData.type) {
      MavenModuleType.MAIN -> configMain(moduleEntity, importFolderHolder, folderImporter)
      MavenModuleType.TEST -> configTest(moduleEntity, importFolderHolder, folderImporter)
      MavenModuleType.AGGREGATOR_MAIN_TEST -> configMainAndTestAggregator(moduleEntity, importFolderHolder, folderImporter)
      MavenModuleType.AGGREGATOR -> configAggregator(moduleEntity, importFolderHolder, folderImporter)
      else -> config(moduleEntity, importFolderHolder, folderImporter)
    }

    return moduleEntity
  }

  private fun config(moduleEntity: ModuleEntity, importFolderHolder: MavenImportFolderHolder, folderImporter: WorkspaceFolderImporter) {
    importJavaSettings(moduleEntity, importFolderHolder)

    folderImporter
      .createContentRoots(moduleEntity, importData, importFolderHolder.excludedFolders, importFolderHolder.generatedFoldersHolder)
  }

  private fun configAggregator(moduleEntity: ModuleEntity,
                               importFolderHolder: MavenImportFolderHolder,
                               folderImporter: WorkspaceFolderImporter) {
    importJavaSettingsAggregator(moduleEntity)

    folderImporter
      .createContentRoots(moduleEntity, importData, importFolderHolder.excludedFolders, importFolderHolder.generatedFoldersHolder, true)
  }

  private fun configMainAndTestAggregator(moduleEntity: ModuleEntity,
                                          importFolderHolder: MavenImportFolderHolder,
                                          folderImporter: WorkspaceFolderImporter) {
    importJavaSettingsMainAndTestAggregator(moduleEntity)

    folderImporter
      .createContentRoots(moduleEntity, importData, importFolderHolder.excludedFolders, null, true)
  }

  private fun configMain(moduleEntity: ModuleEntity,
                         importFolderHolder: MavenImportFolderHolder,
                         folderImporter: WorkspaceFolderImporter) {
    importJavaSettingsMain(moduleEntity, importFolderHolder)

    folderImporter
      .createContentRoots(moduleEntity, importData, importFolderHolder.excludedFolders, importFolderHolder.generatedFoldersHolder.toMain())
  }

  private fun configTest(moduleEntity: ModuleEntity,
                         importFolderHolder: MavenImportFolderHolder,
                         folderImporter: WorkspaceFolderImporter) {
    importJavaSettingsTest(moduleEntity, importFolderHolder)

    folderImporter
      .createContentRoots(moduleEntity, importData, importFolderHolder.excludedFolders, importFolderHolder.generatedFoldersHolder.toTest())
  }

  private fun importJavaSettings(moduleEntity: ModuleEntity, importFolderHolder: MavenImportFolderHolder) {
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

  private fun importJavaSettingsMainAndTestAggregator(moduleEntity: ModuleEntity) {
    val languageLevel = MavenModelUtil.getLanguageLevel(importData.mavenProject) { importData.moduleData.sourceLanguageLevel }
    builder.addJavaModuleSettingsEntity(true, false, null, null, languageLevel.name, moduleEntity, moduleEntity.entitySource)
  }

  private fun importJavaSettingsAggregator(moduleEntity: ModuleEntity) {
    val languageLevel = MavenModelUtil.getLanguageLevel(importData.mavenProject) { importData.moduleData.sourceLanguageLevel }
    builder.addJavaModuleSettingsEntity(true, false, null, null, languageLevel.name, moduleEntity, moduleEntity.entitySource)
  }

  private fun importJavaSettingsMain(moduleEntity: ModuleEntity, importFolderHolder: MavenImportFolderHolder) {
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

  private fun importJavaSettingsTest(moduleEntity: ModuleEntity, importFolderHolder: MavenImportFolderHolder) {
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

  private fun collectMavenFolders(): MavenImportFolderHolder {
    val mavenProject = importData.mavenProject
    val outputPath = toAbsolutePath(mavenProject.outputDirectory)
    val testOutputPath = toAbsolutePath(mavenProject.testOutputDirectory)
    val targetDirPath = toAbsolutePath(mavenProject.buildDirectory)

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
      if (File(annotationProcessorDirectory).list().isNullOrEmpty()) annotationProcessorDirectory = null;
      if (File(annotationProcessorTestDirectory).list().isNullOrEmpty()) annotationProcessorTestDirectory = null;
    }

    val generatedDir = mavenProject.getGeneratedSourcesDirectory(false)
    val generatedDirTest = mavenProject.getGeneratedSourcesDirectory(true)
    val targetChildren = File(targetDirPath).listFiles()
    if (targetChildren != null) {
      for (f in targetChildren) {
        if (!f.isDirectory) continue
        if (FileUtil.pathsEqual(generatedDir, f.path)) {
          generatedSourceFolder = toAbsolutePath(generatedDir)
        }
        else if (FileUtil.pathsEqual(generatedDirTest, f.path)) {
          generatedTestSourceFolder = toAbsolutePath(generatedDirTest)
        }
      }
    }
    val generatedFoldersHolder = GeneratedFoldersHolder(annotationProcessorDirectory, annotationProcessorTestDirectory,
                                                        generatedSourceFolder, generatedTestSourceFolder)

    return MavenImportFolderHolder(outputPath, testOutputPath, targetDirPath, excludedFolders, generatedFoldersHolder)
  }

  private fun collectDependencies(importData: MavenModuleImportData, entitySource: EntitySource): List<ModuleDependencyItem> {
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

  private fun toAbsolutePath(path: String) = MavenUtil.toPath(importData.mavenProject, path).path

  companion object {
    internal val JAVADOC_TYPE: LibraryRootTypeId = LibraryRootTypeId("JAVADOC")
  }
}

class MavenImportFolderHolder(
  val outputPath: String,
  val testOutputPath: String,
  val targetDirPath: String,
  val excludedFolders: List<String>,
  val generatedFoldersHolder: GeneratedFoldersHolder
)