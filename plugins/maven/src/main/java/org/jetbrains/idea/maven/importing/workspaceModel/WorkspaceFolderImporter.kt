// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.FileCollectionFactory
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
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenImportingSettings.GeneratedSourcesFolder.*
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer
import java.io.File

class WorkspaceFolderImporter(
  private val builder: MutableEntityStorage,
  private val virtualFileUrlManager: VirtualFileUrlManager,
  private val importingSettings: MavenImportingSettings,
  private val importingContext: FolderImportingContext) {

  fun createContentRoots(module: ModuleEntity, importData: MavenModuleImportData): CachedProjectFolders {
    val allFolders = mutableListOf<ContentRootCollector.Folder>()

    val cachedFolders = importingContext.projectIdToCachedFolders.getOrPut(importData.mavenProject.mavenId.key) {
      collectMavenFolders(importData)
    }

    addContentRoot(cachedFolders, allFolders)
    addSourceFolders(importData, allFolders)
    addCachedFolders(importData, cachedFolders, allFolders)

    for (rootInfo in ContentRootCollector.collect(allFolders)) {
      if (!File(rootInfo.path).exists()) continue

      val excludedUrls = rootInfo.folders.filterIsInstance<ContentRootCollector.BaseExcludedFolder>().map {
        virtualFileUrlManager.fromPath(it.path)
      }
      val contentRootEntity = builder.addContentRootEntity(virtualFileUrlManager.fromPath(rootInfo.path),
                                                           excludedUrls,
                                                           emptyList(), module)
      rootInfo.folders.forEach {
        if (it is ContentRootCollector.UserOrGeneratedSourceFolder) registerSourceRootFolder(contentRootEntity, it)
      }
    }

    return cachedFolders
  }

  private fun addContentRoot(cachedFolders: CachedProjectFolders,
                             allFolders: MutableList<ContentRootCollector.Folder>) {
    val contentRoot = cachedFolders.projectContentRootPath

    // make sure we don't have overlapping content roots in different modules
    val alreadyRegisteredRoot = importingContext.alreadyRegisteredContentRoots.contains(contentRoot)
    if (alreadyRegisteredRoot) return

    allFolders.add(ContentRootCollector.ContentRootFolder(contentRoot))
    importingContext.alreadyRegisteredContentRoots.add(contentRoot)
  }

  private fun addSourceFolders(importData: MavenModuleImportData,
                               allFolders: MutableList<ContentRootCollector.Folder>) {
    val sourceFolders = when (importData.moduleData.type) {
      MavenModuleType.MAIN -> MavenFoldersImporter.getMainSourceFolders(importData.mavenProject)
      MavenModuleType.TEST -> MavenFoldersImporter.getTestSourceFolders(importData.mavenProject)
      MavenModuleType.AGGREGATOR_MAIN_TEST -> emptyMap()
      MavenModuleType.AGGREGATOR -> emptyMap()
      else -> MavenFoldersImporter.getSourceFolders(importData.mavenProject)
    }

    sourceFolders.forEach { (path, type) -> allFolders.add(ContentRootCollector.SourceFolder(path, type)) }
  }

  private fun addCachedFolders(importData: MavenModuleImportData,
                               cachedFolders: CachedProjectFolders,
                               allFolders: MutableList<ContentRootCollector.Folder>) {
    fun includeIf(it: ContentRootCollector.Folder, forTests: Boolean) =
      when (it) {
        is ContentRootCollector.UserOrGeneratedSourceFolder -> it.type.isForTests == forTests
        else -> true
      }

    fun exceptSources(it: ContentRootCollector.Folder) = it !is ContentRootCollector.UserOrGeneratedSourceFolder

    allFolders.addAll(when (importData.moduleData.type) {
                        MavenModuleType.MAIN -> cachedFolders.folders.filter { includeIf(it, forTests = false) }
                        MavenModuleType.TEST -> cachedFolders.folders.filter { includeIf(it, forTests = true) }
                        MavenModuleType.AGGREGATOR_MAIN_TEST -> cachedFolders.folders.filter { exceptSources(it) }
                        else -> cachedFolders.folders
                      })
  }

  private fun registerSourceRootFolder(contentRootEntity: ContentRootEntity,
                                       folder: ContentRootCollector.UserOrGeneratedSourceFolder) {
    if (!File(folder.path).exists()) return

    val rootType = when (folder.type) {
      JavaSourceRootType.SOURCE -> JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID
      JavaSourceRootType.TEST_SOURCE -> JpsModuleRootModelSerializer.JAVA_TEST_ROOT_TYPE_ID
      JavaResourceRootType.RESOURCE -> JpsJavaModelSerializerExtension.JAVA_RESOURCE_ROOT_ID
      JavaResourceRootType.TEST_RESOURCE -> JpsJavaModelSerializerExtension.JAVA_TEST_RESOURCE_ROOT_ID
      else -> error("${folder.type} doesn't  match to maven root item")
    }

    val sourceRootEntity = builder.addSourceRootEntity(contentRootEntity,
                                                       virtualFileUrlManager.fromPath(folder.path),
                                                       rootType,
                                                       contentRootEntity.entitySource)

    val isResource = JpsJavaModelSerializerExtension.JAVA_RESOURCE_ROOT_ID == rootType
                     || JpsJavaModelSerializerExtension.JAVA_TEST_RESOURCE_ROOT_ID == rootType

    val isGenerated = folder is ContentRootCollector.BaseGeneratedSourceFolder

    if (isResource) {
      builder.addJavaResourceRootEntity(sourceRootEntity, isGenerated, "")
    }
    else {
      builder.addJavaSourceRootEntity(sourceRootEntity, isGenerated, "")
    }
  }

  private fun collectMavenFolders(importData: MavenModuleImportData): CachedProjectFolders { // extract
    val mavenProject = importData.mavenProject
    fun toAbsolutePath(path: String) = MavenUtil.toPath(mavenProject, path).path

    val outputPath = toAbsolutePath(mavenProject.outputDirectory)
    val testOutputPath = toAbsolutePath(mavenProject.testOutputDirectory)
    val targetDirPath = toAbsolutePath(mavenProject.buildDirectory)

    val folders = mutableListOf<ContentRootCollector.Folder>()

    if (importingSettings.isExcludeTargetFolder) {
      folders.add(ContentRootCollector.ExcludedFolder(targetDirPath))
    }
    if (!FileUtil.isAncestor(targetDirPath, outputPath, false)) { //todo  remove ifs?
      folders.add(ContentRootCollector.ExcludedFolder(outputPath))
    }
    if (!FileUtil.isAncestor(targetDirPath, testOutputPath, false)) {
      folders.add(ContentRootCollector.ExcludedFolder(testOutputPath))
    }

    for (each in importData.mavenProject.suitableImporters) {
      val excludes = mutableListOf<String>()
      each.collectExcludedFolders(importData.mavenProject, excludes)
      excludes.forEach { folders.add(ContentRootCollector.ExcludedFolderAndPreventGeneratedSubfolders(toAbsolutePath(it))) }
    }

    val generatedSourceFolders = GeneratedFoldersCollector(folders, JavaSourceRootType.SOURCE)
    val generatedTestSourceFolders = GeneratedFoldersCollector(folders, JavaSourceRootType.TEST_SOURCE)

    if (importingSettings.generatedSourcesFolder != MavenImportingSettings.GeneratedSourcesFolder.IGNORE) {
      generatedSourceFolders.addOptional(File(mavenProject.getAnnotationProcessorDirectory(false)))
      generatedTestSourceFolders.addOptional(File(mavenProject.getAnnotationProcessorDirectory(true)))

      val generatedDir = mavenProject.getGeneratedSourcesDirectory(false)
      val generatedDirTest = mavenProject.getGeneratedSourcesDirectory(true)

      addTargetFolders(File(toAbsolutePath(generatedDir)), generatedSourceFolders)
      addTargetFolders(File(toAbsolutePath(generatedDirTest)), generatedTestSourceFolders)
    }

    return CachedProjectFolders(importData.mavenProject.directory, outputPath, testOutputPath, folders)
  }

  private class GeneratedFoldersCollector(val result: MutableList<ContentRootCollector.Folder>,
                                          val type: JpsModuleSourceRootType<*>) {
    fun addExcplicit(dir: File) = doAdd(dir, ContentRootCollector.ExplicitGeneratedSourceFolder(dir.path, type))
    fun addOptional(dir: File) = doAdd(dir, ContentRootCollector.OptionalGeneratedSourceFolder(dir.path, type))

    private fun doAdd(dir: File, info: ContentRootCollector.BaseGeneratedSourceFolder) {
      val isNotEmptyDirectory = !dir.listFiles().isNullOrEmpty()
      if (isNotEmptyDirectory) {
        result.add(info)
      }
    }
  }

  private fun addTargetFolders(targetDir: File, result: GeneratedFoldersCollector) {
    fun addAllSubDirs(dir: File) = dir.listFiles()?.forEach { result.addExcplicit(it) } // todo add optional?

    when (importingSettings.generatedSourcesFolder) {
      GENERATED_SOURCE_FOLDER -> result.addExcplicit(targetDir)
      SUBFOLDER -> addAllSubDirs(targetDir)
      AUTODETECT -> {
        for (it in JavaSourceRootDetectionUtil.suggestRoots(targetDir)) {
          val suggestedDir = it.directory
          result.addExcplicit(suggestedDir)

          val suggestedRootPointAtTargetDir = FileUtil.filesEqual(suggestedDir, targetDir)
          if (suggestedRootPointAtTargetDir) return
        }
        addAllSubDirs(targetDir)
      }
      MavenImportingSettings.GeneratedSourcesFolder.IGNORE -> {}
    }
  }

  class FolderImportingContext {
    internal val projectIdToCachedFolders = mutableMapOf<String, CachedProjectFolders>()
    internal val alreadyRegisteredContentRoots = FileCollectionFactory.createCanonicalFilePathSet()
  }

  class CachedProjectFolders(
    val projectContentRootPath: String,
    val outputPath: String,
    val testOutputPath: String,
    val folders: List<ContentRootCollector.Folder>
  )
}
