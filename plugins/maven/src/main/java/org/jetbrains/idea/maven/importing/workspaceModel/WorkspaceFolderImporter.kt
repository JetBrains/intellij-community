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
import org.jetbrains.idea.maven.importing.BuildHelperMavenPluginUtil
import org.jetbrains.idea.maven.importing.MavenImporter
import org.jetbrains.idea.maven.importing.MavenModuleType
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenImportingSettings.GeneratedSourcesFolder.*
import org.jetbrains.idea.maven.project.MavenProject
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

  fun createContentRoots(mavenProject: MavenProject, moduleType: MavenModuleType, module: ModuleEntity): CachedProjectFolders {
    val allFolders = mutableListOf<ContentRootCollector.ImportedFolder>()

    val cachedFolders = importingContext.projectToCachedFolders.getOrPut(mavenProject) {
      collectMavenFolders(mavenProject)
    }

    addContentRoot(cachedFolders, allFolders)
    addCachedFolders(moduleType, cachedFolders, allFolders)

    for (root in ContentRootCollector.collect(allFolders)) {
      if (!File(root.path).exists()) continue

      val excludedUrls = root.excludeFolders.map { exclude -> virtualFileUrlManager.fromPath(exclude.path) }
      val contentRootEntity = builder.addContentRootEntity(virtualFileUrlManager.fromPath(root.path),
                                                           excludedUrls,
                                                           emptyList(), module)
      root.sourceFolders.forEach { folder ->
        if (!File(folder.path).exists()) return@forEach
        registerSourceRootFolder(contentRootEntity, folder)
      }
    }

    return cachedFolders
  }

  private fun addContentRoot(cachedFolders: CachedProjectFolders,
                             allFolders: MutableList<ContentRootCollector.ImportedFolder>) {
    val contentRoot = cachedFolders.projectContentRootPath

    // make sure we don't have overlapping content roots in different modules
    val alreadyRegisteredRoot = importingContext.alreadyRegisteredContentRoots.contains(contentRoot)
    if (alreadyRegisteredRoot) return

    allFolders.add(ContentRootCollector.ProjectRootFolder(contentRoot))
    importingContext.alreadyRegisteredContentRoots.add(contentRoot)
  }

  private fun addCachedFolders(moduleType: MavenModuleType,
                               cachedFolders: CachedProjectFolders,
                               allFolders: MutableList<ContentRootCollector.ImportedFolder>) {
    fun includeIf(it: ContentRootCollector.ImportedFolder, forTests: Boolean) =
      when (it) {
        is ContentRootCollector.UserOrGeneratedSourceFolder -> it.type.isForTests == forTests
        else -> true
      }

    fun exceptSources(it: ContentRootCollector.ImportedFolder) = it !is ContentRootCollector.UserOrGeneratedSourceFolder

    allFolders.addAll(when (moduleType) {
                        MavenModuleType.MAIN_ONLY -> cachedFolders.folders.filter { includeIf(it, forTests = false) }
                        MavenModuleType.TEST_ONLY -> cachedFolders.folders.filter { includeIf(it, forTests = true) }
                        MavenModuleType.COMPOUND_MODULE -> cachedFolders.folders.filter { exceptSources(it) }
                        MavenModuleType.AGGREGATOR -> emptyList()
                        else -> cachedFolders.folders
                      })
  }

  private fun registerSourceRootFolder(contentRootEntity: ContentRootEntity,
                                       folder: ContentRootCollector.SourceFolderResult) {
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

    if (isResource) {
      builder.addJavaResourceRootEntity(sourceRootEntity, folder.isGenerated, "")
    }
    else {
      builder.addJavaSourceRootEntity(sourceRootEntity, folder.isGenerated, "")
    }
  }

  private fun collectMavenFolders(mavenProject: MavenProject): CachedProjectFolders {
    fun toAbsolutePath(path: String) = MavenUtil.toPath(mavenProject, path).path

    val outputPath = toAbsolutePath(mavenProject.outputDirectory)
    val testOutputPath = toAbsolutePath(mavenProject.testOutputDirectory)
    val targetDirPath = toAbsolutePath(mavenProject.buildDirectory)

    val folders = mutableListOf<ContentRootCollector.ImportedFolder>()

    collectSourceFolders(mavenProject, folders)

    if (importingSettings.isExcludeTargetFolder) {
      folders.add(ContentRootCollector.ExcludedFolder(targetDirPath))
    }
    if (!FileUtil.isAncestor(targetDirPath, outputPath, false)) {
      folders.add(ContentRootCollector.ExcludedFolder(outputPath))
    }
    if (!FileUtil.isAncestor(targetDirPath, testOutputPath, false)) {
      folders.add(ContentRootCollector.ExcludedFolder(testOutputPath))
    }

    for (each in MavenImporter.getSuitableImporters(mavenProject)) {
      val excludes = mutableListOf<String>()
      each.collectExcludedFolders(mavenProject, excludes)
      excludes.forEach { folders.add(ContentRootCollector.ExcludedFolderAndPreventSubfolders(toAbsolutePath(it))) }
    }

    val generatedSourceFolders = GeneratedFoldersCollector(folders, JavaSourceRootType.SOURCE)
    val generatedTestSourceFolders = GeneratedFoldersCollector(folders, JavaSourceRootType.TEST_SOURCE)

    if (importingSettings.generatedSourcesFolder != MavenImportingSettings.GeneratedSourcesFolder.IGNORE) {
      generatedSourceFolders.addAnnotationSources(File(mavenProject.getAnnotationProcessorDirectory(false)))
      generatedTestSourceFolders.addAnnotationSources(File(mavenProject.getAnnotationProcessorDirectory(true)))

      val generatedDir = mavenProject.getGeneratedSourcesDirectory(false)
      val generatedDirTest = mavenProject.getGeneratedSourcesDirectory(true)

      collectTargetFolders(File(toAbsolutePath(generatedDir)), generatedSourceFolders)
      collectTargetFolders(File(toAbsolutePath(generatedDirTest)), generatedTestSourceFolders)
    }

    return CachedProjectFolders(mavenProject.directory, outputPath, testOutputPath, folders)
  }


  private fun collectSourceFolders(mavenProject: MavenProject, result: MutableList<ContentRootCollector.ImportedFolder>) {
    fun toAbsolutePath(path: String) = MavenUtil.toPath(mavenProject, path).path

    mavenProject.sources.forEach { result.add(ContentRootCollector.SourceFolder(it, JavaSourceRootType.SOURCE)) }
    mavenProject.resources.forEach { result.add(ContentRootCollector.SourceFolder(it.directory, JavaResourceRootType.RESOURCE)) }

    mavenProject.testSources.forEach { result.add(ContentRootCollector.SourceFolder(it, JavaSourceRootType.TEST_SOURCE)) }
    mavenProject.testResources.forEach { result.add(ContentRootCollector.SourceFolder(it.directory, JavaResourceRootType.TEST_RESOURCE)) }

    for (each in MavenImporter.getSuitableImporters(mavenProject)) {
      each.collectSourceRoots(mavenProject) { path: String, type: JpsModuleSourceRootType<*> ->
        result.add(ContentRootCollector.SourceFolder(toAbsolutePath(path), type))
      }
    }

    BuildHelperMavenPluginUtil.addBuilderHelperPaths(mavenProject, "add-source") { path ->
      result.add(ContentRootCollector.SourceFolder(toAbsolutePath(path), JavaSourceRootType.SOURCE))
    }
    BuildHelperMavenPluginUtil.addBuilderHelperResourcesPaths(mavenProject, "add-resource") { path ->
      result.add(ContentRootCollector.SourceFolder(toAbsolutePath(path), JavaResourceRootType.RESOURCE))
    }

    BuildHelperMavenPluginUtil.addBuilderHelperPaths(mavenProject, "add-test-source") { path ->
      result.add(ContentRootCollector.SourceFolder(toAbsolutePath(path), JavaSourceRootType.TEST_SOURCE))
    }
    BuildHelperMavenPluginUtil.addBuilderHelperResourcesPaths(mavenProject, "add-test-resource") { path ->
      result.add(ContentRootCollector.SourceFolder(toAbsolutePath(path), JavaResourceRootType.TEST_RESOURCE))
    }
  }

  private class GeneratedFoldersCollector(val result: MutableList<ContentRootCollector.ImportedFolder>,
                                          val type: JpsModuleSourceRootType<*>) {
    fun addGeneratedSources(dir: File) = doAdd(dir, ContentRootCollector.GeneratedSourceFolder(dir.path, type))
    fun addAnnotationSources(dir: File) = doAdd(dir, ContentRootCollector.AnnotationSourceFolder(dir.path, type))

    private fun doAdd(dir: File, info: ContentRootCollector.BaseGeneratedSourceFolder) {
      val isNotEmptyDirectory = !dir.listFiles().isNullOrEmpty()
      if (isNotEmptyDirectory) {
        result.add(info)
      }
    }
  }

  private fun collectTargetFolders(targetDir: File, result: GeneratedFoldersCollector) {
    fun addAllSubDirs(dir: File) = dir.listFiles()?.forEach { result.addGeneratedSources(it) }

    when (importingSettings.generatedSourcesFolder) {
      GENERATED_SOURCE_FOLDER -> result.addGeneratedSources(targetDir)
      SUBFOLDER -> addAllSubDirs(targetDir)
      AUTODETECT -> {
        for (it in JavaSourceRootDetectionUtil.suggestRoots(targetDir)) {
          val suggestedDir = it.directory
          result.addGeneratedSources(suggestedDir)

          val suggestedRootPointAtTargetDir = FileUtil.filesEqual(suggestedDir, targetDir)
          if (suggestedRootPointAtTargetDir) return
        }
        addAllSubDirs(targetDir)
      }
      MavenImportingSettings.GeneratedSourcesFolder.IGNORE -> {}
    }
  }

  class FolderImportingContext {
    internal val projectToCachedFolders = mutableMapOf<MavenProject, CachedProjectFolders>()
    internal val alreadyRegisteredContentRoots = FileCollectionFactory.createCanonicalFilePathSet()
  }

  class CachedProjectFolders(
    val projectContentRootPath: String,
    val outputPath: String,
    val testOutputPath: String,
    val folders: List<ContentRootCollector.ImportedFolder>
  )
}
