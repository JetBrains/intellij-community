// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.addContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addJavaResourceRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addJavaSourceRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addSourceRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenFoldersImporter
import org.jetbrains.idea.maven.importing.tree.MavenModuleImportData
import org.jetbrains.idea.maven.importing.tree.MavenModuleType
import org.jetbrains.idea.maven.importing.workspaceModel.ContentRootCollector.collect
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenImportingSettings.GeneratedSourcesFolder.*
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import java.io.File
import java.nio.file.Path

class WorkspaceFolderImporter(
  private val builder: MutableEntityStorage,
  private val virtualFileUrlManager: VirtualFileUrlManager,
  private val importingSettings: MavenImportingSettings,
  private val importFoldersByMavenIdCache: MutableMap<String, MavenImportFolderHolder>) {

  fun createContentRoots(module: ModuleEntity, importData: MavenModuleImportData): MavenImportFolderHolder {
    val importFolderHolder = importFoldersByMavenIdCache.getOrPut(importData.mavenProject.mavenId.key) { collectMavenFolders(importData) }

    val baseContentRoot = getBaseContentRoot(importData)
    val folderItemMap = getFolderItemMap(importData)

    val generatedFolders = when (importData.moduleData.type) {
      MavenModuleType.MAIN -> importFolderHolder.generatedFoldersHolder.toMain()
      MavenModuleType.TEST -> importFolderHolder.generatedFoldersHolder.toTest()
      MavenModuleType.AGGREGATOR_MAIN_TEST -> null
      else -> importFolderHolder.generatedFoldersHolder
    }

    val contentRootDataHolders = collect(listOf(baseContentRoot), folderItemMap,
                                         importFolderHolder.excludedFoldersOfNoSourceSubFolder,
                                         importFolderHolder.doNotRegisterSourcesUnder,
                                         generatedFolders)

    for (rootInfo in contentRootDataHolders) {
      val excludedUrls = rootInfo.folders.filterIsInstance<ContentRootCollector.BaseExcludedFolderInfo>().map {
        virtualFileUrlManager.fromPath(it.path)
      }

      val contentRootEntity = builder.addContentRootEntity(virtualFileUrlManager.fromPath(rootInfo.path),
                                                           excludedUrls,
                                                           emptyList(), module)

      rootInfo.folders.forEach {
        if (it is ContentRootCollector.BaseSourceFolderInfo) addSourceRootFolder(contentRootEntity, it)
      }
    }

    return importFolderHolder
  }

  private fun addSourceRootFolder(contentRootEntity: ContentRootEntity,
                                  folder: ContentRootCollector.BaseSourceFolderInfo) {
    val isGenerated = folder is ContentRootCollector.BaseGeneratedSourceFolderInfo

    if (!shouldAddSourceRootFor(folder.path)) return

    val sourceRootEntity = builder
      .addSourceRootEntity(contentRootEntity,
                           virtualFileUrlManager.fromPath(folder.path),
                           folder.rootType,
                           contentRootEntity.entitySource)

    if (folder.isResource()) {
      builder.addJavaResourceRootEntity(sourceRootEntity, isGenerated, "")
    }
    else {
      builder.addJavaSourceRootEntity(sourceRootEntity, isGenerated, "")
    }
  }

  private fun shouldAddSourceRootFor(path: String): Boolean {
    return File(path).exists()
  }

  private fun collectMavenFolders(importData: MavenModuleImportData): MavenImportFolderHolder { // extract
    val mavenProject = importData.mavenProject
    fun toAbsolutePath(path: String) = MavenUtil.toPath(mavenProject, path).path

    val outputPath = toAbsolutePath(mavenProject.outputDirectory)
    val testOutputPath = toAbsolutePath(mavenProject.testOutputDirectory)
    val targetDirPath = toAbsolutePath(mavenProject.buildDirectory)

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

    val generatedSourceFolders = GeneratedFoldersCollector()
    val generatedTestSourceFolders = GeneratedFoldersCollector()

    if (importingSettings.generatedSourcesFolder != MavenImportingSettings.GeneratedSourcesFolder.IGNORE) {
      generatedSourceFolders.addOptional(File(mavenProject.getAnnotationProcessorDirectory(false)))
      generatedSourceFolders.addOptional(File(mavenProject.getAnnotationProcessorDirectory(true)))

      val generatedDir = mavenProject.getGeneratedSourcesDirectory(false)
      val generatedDirTest = mavenProject.getGeneratedSourcesDirectory(true)

      addTargetFolders(File(toAbsolutePath(generatedDir)), generatedSourceFolders)
      addTargetFolders(File(toAbsolutePath(generatedDirTest)), generatedTestSourceFolders)
    }

    val generatedFoldersHolder = GeneratedFoldersHolder(generatedSourceFolders.generated,
                                                        generatedTestSourceFolders.generated,
                                                        generatedSourceFolders.optional,
                                                        generatedTestSourceFolders.optional)

    return MavenImportFolderHolder(outputPath, testOutputPath, targetDirPath, excludedFoldersOfNoSourceSubFolder, doNotRegisterSourcesUnder,
                                   generatedFoldersHolder)
  }

  private class GeneratedFoldersCollector {
    val generated = mutableListOf<String>()
    val optional = mutableListOf<String>()

    fun addGenerated(dir: File) {
      val isNotEmptyDirectory = !dir.listFiles().isNullOrEmpty()
      if (isNotEmptyDirectory) generated.add(dir.path)
    }

    fun addOptional(dir: File) {
      val isNotEmptyDirectory = !dir.listFiles().isNullOrEmpty()
      if (isNotEmptyDirectory) optional.add(dir.path)
    }
  }

  private fun addTargetFolders(targetDir: File, result: GeneratedFoldersCollector) {
    fun addAllSubDirs(dir: File) = dir.listFiles()?.forEach { result.addGenerated(it) }

    when (importingSettings.generatedSourcesFolder) {
      GENERATED_SOURCE_FOLDER -> result.addGenerated(targetDir)
      SUBFOLDER -> addAllSubDirs(targetDir)
      AUTODETECT -> {
        for (it in JavaSourceRootDetectionUtil.suggestRoots(targetDir)) {
          val suggestedDir = it.directory
          result.addGenerated(suggestedDir)

          val suggestedRootPointAtTargetDir = FileUtil.filesEqual(suggestedDir, targetDir)
          if (suggestedRootPointAtTargetDir) return
        }
        addAllSubDirs(targetDir)
      }
      MavenImportingSettings.GeneratedSourcesFolder.IGNORE -> {}
    }
  }

  class MavenImportFolderHolder(
    val outputPath: String,
    val testOutputPath: String,
    val targetDirPath: String,
    val excludedFoldersOfNoSourceSubFolder: List<String>,
    val doNotRegisterSourcesUnder: List<String>,
    val generatedFoldersHolder: GeneratedFoldersHolder
  )

  private fun getBaseContentRoot(importData: MavenModuleImportData): String {
    return when (importData.moduleData.type) {
      MavenModuleType.MAIN -> Path.of(importData.mavenProject.directory, "src", "main").toString()
      MavenModuleType.TEST -> Path.of(importData.mavenProject.directory, "src", "test").toString()
      else -> Path.of(importData.mavenProject.directory).toString()
    }
  }

  private fun getFolderItemMap(importData: MavenModuleImportData): Map<String, JpsModuleSourceRootType<*>> {
    return when (importData.moduleData.type) {
      MavenModuleType.MAIN -> MavenFoldersImporter.getMainSourceFolders(importData.mavenProject)
      MavenModuleType.TEST -> MavenFoldersImporter.getTestSourceFolders(importData.mavenProject)
      MavenModuleType.AGGREGATOR_MAIN_TEST -> emptyMap()
      MavenModuleType.AGGREGATOR -> emptyMap()
      else -> MavenFoldersImporter.getSourceFolders(importData.mavenProject)
    }
  }

}
