// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.bridgeEntities.api.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenFoldersImporter
import org.jetbrains.idea.maven.importing.tree.MavenModuleImportData
import org.jetbrains.idea.maven.importing.tree.MavenModuleType
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenImportingSettings.GeneratedSourcesFolder.*
import org.jetbrains.idea.maven.project.MavenProject
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

    val contentRootDataHolders = ContentRootCollector.collect(baseContentRoot,
                                                              folderItemMap,
                                                              importFolderHolder.excludedFoldersOfNoSourceSubFolder,
                                                              importFolderHolder.doNotRegisterSourcesUnder,
                                                              generatedFolders)

    for (dataHolder in contentRootDataHolders) {
      val contentRootEntity = builder
        .addContentRootEntity(virtualFileUrlManager.fromPath(dataHolder.contentRoot),
                              dataHolder.excludedPaths.map { virtualFileUrlManager.fromPath(it) },
                              emptyList(), module)

      for (sourceFolder in dataHolder.sourceFolders) {
        addSourceRootFolder(contentRootEntity, sourceFolder)
      }

      for (folder in dataHolder.generatedFolders) {
        configGeneratedSourceFolder(File(folder.path), folder.rootType, contentRootEntity)
      }

      for (folder in dataHolder.annotationProcessorFolders) {
        addGeneratedJavaSourceFolderIfNoRegisteredSourceOnThisPath(folder.path, folder.rootType, contentRootEntity)
      }
    }

    return importFolderHolder
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
    if (importingSettings.generatedSourcesFolder != MavenImportingSettings.GeneratedSourcesFolder.IGNORE) {
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

  private fun addSourceRootFolder(contentRootEntity: ContentRootEntity,
                                  sourceFolder: SourceFolderData) {
    if (!shouldAddSourceRootFor(sourceFolder.path, onlyIfNotEmpty = false)) return

    val sourceRootEntity = builder
      .addSourceRootEntity(contentRootEntity,
                           virtualFileUrlManager.fromUrl(VfsUtilCore.pathToUrl(sourceFolder.path)),
                           sourceFolder.rootType,
                           contentRootEntity.entitySource)

    if (sourceFolder.isResource()) {
      builder.addJavaResourceRootEntity(sourceRootEntity, false, "")
    }
    else {
      builder.addJavaSourceRootEntity(sourceRootEntity, false, "")
    }
  }

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

  private fun addGeneratedJavaSourceFolderIfNoRegisteredSourceOnThisPath(path: String,
                                                                         rootType: String,
                                                                         contentRootEntity: ContentRootEntity) {
    if (!shouldAddSourceRootFor(path, onlyIfNotEmpty = true)) return

    val url = virtualFileUrlManager.fromPath(path)
    if (contentRootEntity.sourceRoots.any {
        VfsUtilCore.isEqualOrAncestor(url.url, it.url.url) || VfsUtilCore.isEqualOrAncestor(it.url.url, url.url)
      }) return

    val sourceRootEntity = builder.addSourceRootEntity(contentRootEntity, url,
                                                       rootType,
                                                       contentRootEntity.entitySource)
    builder.addJavaSourceRootEntity(sourceRootEntity, true, "")
  }

  private fun shouldAddSourceRootFor(path: String, onlyIfNotEmpty: Boolean): Boolean {
    if (onlyIfNotEmpty) {
      return !File(path).list().isNullOrEmpty()
    }
    else {
      return File(path).exists()
    }
  }

  private fun configGeneratedSourceFolder(targetDir: File, rootType: String, contentRootEntity: ContentRootEntity) {
    when (importingSettings.generatedSourcesFolder) {
      GENERATED_SOURCE_FOLDER -> addGeneratedJavaSourceFolderIfNoRegisteredSourceOnThisPath(targetDir.path, rootType, contentRootEntity)
      SUBFOLDER -> addAllSubDirsAsGeneratedSources(targetDir, rootType, contentRootEntity)
      AUTODETECT -> {
        val sourceRoots = JavaSourceRootDetectionUtil.suggestRoots(targetDir)
        for (root in sourceRoots) {
          if (FileUtil.filesEqual(targetDir, root.directory)) {
            addGeneratedJavaSourceFolderIfNoRegisteredSourceOnThisPath(targetDir.path, rootType, contentRootEntity)
            return
          }
          addAsGeneratedSourceFolder(root.directory, rootType, contentRootEntity)
        }
        addAllSubDirsAsGeneratedSources(targetDir, rootType, contentRootEntity)
      }
      MavenImportingSettings.GeneratedSourcesFolder.IGNORE -> {}
    }
  }

  private fun addAsGeneratedSourceFolder(dir: File, rootType: String, contentRootEntity: ContentRootEntity) {
    val url = VfsUtilCore.fileToUrl(dir)
    val folder = contentRootEntity.sourceRoots.find { it.url.url == url }
    val hasRegisteredSubfolder = contentRootEntity.sourceRoots.any { VfsUtilCore.isEqualOrAncestor(url, it.url.url) }
    if (!hasRegisteredSubfolder
        || folder != null && (folder.asJavaSourceRoot()?.generated == true || folder.asJavaResourceRoot()?.generated == true)) {
      addGeneratedJavaSourceFolderIfNoRegisteredSourceOnThisPath(dir.path, rootType, contentRootEntity)
    }
  }

  private fun addAllSubDirsAsGeneratedSources(dir: File, rootType: String, contentRootEntity: ContentRootEntity) {
    dir.listFiles()?.forEach { f ->
      if (f.isDirectory) {
        addAsGeneratedSourceFolder(f, rootType, contentRootEntity)
      }
    }
  }
}
