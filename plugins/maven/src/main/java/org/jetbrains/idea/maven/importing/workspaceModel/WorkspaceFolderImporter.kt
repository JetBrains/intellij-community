// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil
import com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.containers.FileCollectionFactory
import org.jetbrains.idea.maven.importing.BuildHelperMavenPluginUtil
import org.jetbrains.idea.maven.importing.MavenImporter
import org.jetbrains.idea.maven.importing.MavenWorkspaceConfigurator
import org.jetbrains.idea.maven.importing.StandardMavenModuleType
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenImportingSettings.GeneratedSourcesFolder.*
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.statistics.MavenImportCollector
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer
import java.io.File
import java.util.stream.Stream

internal class WorkspaceFolderImporter(
  private val builder: MutableEntityStorage,
  private val virtualFileUrlManager: VirtualFileUrlManager,
  private val importingSettings: MavenImportingSettings,
  private val importingContext: FolderImportingContext) {

  fun createContentRoots(mavenProject: MavenProject, moduleType: StandardMavenModuleType, module: ModuleEntity,
                         stats: WorkspaceImportStats): CachedProjectFolders {
    val allFolders = mutableListOf<ContentRootCollector.ImportedFolder>()

    val cachedFolders = importingContext.projectToCachedFolders.getOrPut(mavenProject) {
      collectMavenFolders(mavenProject, stats)
    }

    addContentRoot(cachedFolders, allFolders)
    addCachedFolders(moduleType, cachedFolders, allFolders)

    for (root in ContentRootCollector.collect(allFolders)) {
      val excludes = root.excludeFolders
        .map { exclude -> virtualFileUrlManager.fromUrl(VfsUtilCore.pathToUrl(exclude.path)) }
        .map { builder addEntity ExcludeUrlEntity(it, module.entitySource) }
      val contentRootEntity = builder addEntity ContentRootEntity(virtualFileUrlManager.fromUrl(VfsUtilCore.pathToUrl(root.path)),
                                                                  emptyList(), module.entitySource) {
        this.excludedUrls = excludes
        this.module = module
      }
      root.sourceFolders.forEach { folder ->
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

  private fun addCachedFolders(moduleType: StandardMavenModuleType,
                               cachedFolders: CachedProjectFolders,
                               allFolders: MutableList<ContentRootCollector.ImportedFolder>) {
    fun includeIf(it: ContentRootCollector.ImportedFolder, forTests: Boolean) =
      when (it) {
        is ContentRootCollector.UserOrGeneratedSourceFolder -> it.type.isForTests == forTests
        else -> true
      }

    fun exceptSources(it: ContentRootCollector.ImportedFolder) = it !is ContentRootCollector.UserOrGeneratedSourceFolder

    allFolders.addAll(when (moduleType) {
                        StandardMavenModuleType.MAIN_ONLY -> cachedFolders.folders.filter { includeIf(it, forTests = false) }
                        StandardMavenModuleType.TEST_ONLY -> cachedFolders.folders.filter { includeIf(it, forTests = true) }
                        StandardMavenModuleType.COMPOUND_MODULE -> cachedFolders.folders.filter { exceptSources(it) }
                        StandardMavenModuleType.AGGREGATOR -> emptyList()
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

    val sourceRootEntity = builder addEntity SourceRootEntity(virtualFileUrlManager.fromUrl(VfsUtilCore.pathToUrl(folder.path)), rootType,
                                                              contentRootEntity.entitySource) {
      this.contentRoot = contentRootEntity
    }

    val isResource = JpsJavaModelSerializerExtension.JAVA_RESOURCE_ROOT_ID == rootType
                     || JpsJavaModelSerializerExtension.JAVA_TEST_RESOURCE_ROOT_ID == rootType

    if (isResource) {
      builder addEntity JavaResourceRootPropertiesEntity(folder.isGenerated, "", sourceRootEntity.entitySource) {
        this.sourceRoot = sourceRootEntity
      }
    }
    else {
      builder addEntity JavaSourceRootPropertiesEntity(folder.isGenerated, "", sourceRootEntity.entitySource) {
        this.sourceRoot = sourceRootEntity
      }
    }
  }

  private fun collectMavenFolders(mavenProject: MavenProject, stats: WorkspaceImportStats): CachedProjectFolders {
    val folders = mutableListOf<ContentRootCollector.ImportedFolder>()

    val configuratorContext = object : MavenWorkspaceConfigurator.FoldersContext {
      override val mavenProject = mavenProject
    }
    val legacyImporters = MavenImporter.getSuitableImporters(mavenProject, true)

    collectSourceFolders(mavenProject, folders, configuratorContext, legacyImporters, stats)
    collectGeneratedFolders(folders, mavenProject)

    val outputPath = mavenProject.toAbsolutePath(mavenProject.outputDirectory)
    val testOutputPath = mavenProject.toAbsolutePath(mavenProject.testOutputDirectory)
    val targetDirPath = mavenProject.toAbsolutePath(mavenProject.buildDirectory)

    if (importingSettings.isExcludeTargetFolder) {
      folders.add(ContentRootCollector.ExcludedFolder(targetDirPath))
    }
    if (!FileUtil.isAncestor(targetDirPath, outputPath, false)) {
      folders.add(ContentRootCollector.ExcludedFolder(outputPath))
    }
    if (!FileUtil.isAncestor(targetDirPath, testOutputPath, false)) {
      folders.add(ContentRootCollector.ExcludedFolder(testOutputPath))
    }

    for (each in legacyImporters) {
      val excludes = mutableListOf<String>()
      try {
        each.collectExcludedFolders(mavenProject, excludes)
      }
      catch (e: Exception) {
        MavenLog.LOG.error("Exception in MavenImporter.collectExcludedFolders, skipping it.", e)
      }
      excludes.forEach { folders.add(ContentRootCollector.ExcludedFolderAndPreventSubfolders(mavenProject.toAbsolutePath(it))) }
    }
    for (each in WORKSPACE_CONFIGURATOR_EP.extensionList) {
      stats.recordConfigurator(each, MavenImportCollector.COLLECT_FOLDERS_DURATION_MS) {
        try {
          each.getFoldersToExclude(configuratorContext)
        }
        catch (e: Exception) {
          MavenLog.LOG.error("Exception in MavenWorkspaceConfigurator.getFoldersToExclude, skipping it.", e)
          Stream.empty()
        }
      }.forEach {
        folders.add(ContentRootCollector.ExcludedFolderAndPreventSubfolders(mavenProject.toAbsolutePath(it)))
      }
    }

    return CachedProjectFolders(mavenProject.directory, outputPath, testOutputPath, folders)
  }

  private fun collectGeneratedFolders(folders: MutableList<ContentRootCollector.ImportedFolder>,
                                      mavenProject: MavenProject) {
    val generatedFolderSetting = importingSettings.generatedSourcesFolder
    if (generatedFolderSetting != IGNORE) {
      val mainCollector = GeneratedFoldersCollector(folders, generatedFolderSetting, JavaSourceRootType.SOURCE)
      val testCollector = GeneratedFoldersCollector(folders, generatedFolderSetting, JavaSourceRootType.TEST_SOURCE)

      val mainAnnotationsDir = File(mavenProject.getAnnotationProcessorDirectory(false))
      val testAnnotationsDir = File(mavenProject.getAnnotationProcessorDirectory(true))
      mainCollector.addAnnotationFolder(mainAnnotationsDir)
      testCollector.addAnnotationFolder(testAnnotationsDir)

      val toSkip = FileCollectionFactory.createCanonicalFileSet(listOf(mainAnnotationsDir, testAnnotationsDir))
      mainCollector.collectGeneratedFolders(File(mavenProject.toAbsolutePath(mavenProject.getGeneratedSourcesDirectory(false))), toSkip)
      testCollector.collectGeneratedFolders(File(mavenProject.toAbsolutePath(mavenProject.getGeneratedSourcesDirectory(true))), toSkip)
    }
  }

  private fun collectSourceFolders(mavenProject: MavenProject,
                                   result: MutableList<ContentRootCollector.ImportedFolder>,
                                   configuratorContext: MavenWorkspaceConfigurator.FoldersContext,
                                   legacyImporters: List<MavenImporter>,
                                   stats: WorkspaceImportStats) {
    fun toAbsolutePath(path: String) = MavenUtil.toPath(mavenProject, path).path

    mavenProject.sources.forEach { result.add(ContentRootCollector.SourceFolder(it, JavaSourceRootType.SOURCE)) }
    mavenProject.resources.forEach { result.add(ContentRootCollector.SourceFolder(it.directory, JavaResourceRootType.RESOURCE)) }

    mavenProject.testSources.forEach { result.add(ContentRootCollector.SourceFolder(it, JavaSourceRootType.TEST_SOURCE)) }
    mavenProject.testResources.forEach { result.add(ContentRootCollector.SourceFolder(it.directory, JavaResourceRootType.TEST_RESOURCE)) }

    val buildHelperPlugin = BuildHelperMavenPluginUtil.findPlugin(mavenProject)
    if (buildHelperPlugin != null) {
      BuildHelperMavenPluginUtil.addBuilderHelperPaths(buildHelperPlugin, "add-source") { path ->
        result.add(ContentRootCollector.SourceFolder(toAbsolutePath(path), JavaSourceRootType.SOURCE))
      }
      BuildHelperMavenPluginUtil.addBuilderHelperResourcesPaths(buildHelperPlugin, "add-resource") { path ->
        result.add(ContentRootCollector.SourceFolder(toAbsolutePath(path), JavaResourceRootType.RESOURCE))
      }

      BuildHelperMavenPluginUtil.addBuilderHelperPaths(buildHelperPlugin, "add-test-source") { path ->
        result.add(ContentRootCollector.SourceFolder(toAbsolutePath(path), JavaSourceRootType.TEST_SOURCE))
      }
      BuildHelperMavenPluginUtil.addBuilderHelperResourcesPaths(buildHelperPlugin, "add-test-resource") { path ->
        result.add(ContentRootCollector.SourceFolder(toAbsolutePath(path), JavaResourceRootType.TEST_RESOURCE))
      }
    }

    for (each in WORKSPACE_CONFIGURATOR_EP.extensionList) {
      stats.recordConfigurator(each, MavenImportCollector.COLLECT_FOLDERS_DURATION_MS) {
        try {
          each.getAdditionalSourceFolders(configuratorContext)
        }
        catch (e: Exception) {
          MavenLog.LOG.error("Exception in MavenWorkspaceConfigurator.getAdditionalSourceFolders, skipping it.", e)
          Stream.empty()
        }
      }.forEach {
        result.add(ContentRootCollector.SourceFolder(toAbsolutePath(it), JavaSourceRootType.SOURCE))
      }

      stats.recordConfigurator(each, MavenImportCollector.COLLECT_FOLDERS_DURATION_MS) {
        try {
          each.getAdditionalTestSourceFolders(configuratorContext)
        }
        catch (e: Exception) {
          MavenLog.LOG.error("Exception in MavenWorkspaceConfigurator.getAdditionalTestSourceFolders, skipping it.", e)
          Stream.empty()
        }
      }.forEach {
        result.add(ContentRootCollector.SourceFolder(toAbsolutePath(it), JavaSourceRootType.TEST_SOURCE))
      }
    }

    for (each in legacyImporters) {
      try {
        each.collectSourceRoots(mavenProject) { path: String, type: JpsModuleSourceRootType<*> ->
          result.add(ContentRootCollector.SourceFolder(toAbsolutePath(path), type))
        }
      }
      catch (e: Exception) {
        MavenLog.LOG.error("Exception in MavenImporter.collectSourceRoots, skipping it.", e)
      }
    }
  }

  private fun MavenProject.toAbsolutePath(path: String) = MavenUtil.toPath(this, path).path

  private class GeneratedFoldersCollector(val result: MutableList<ContentRootCollector.ImportedFolder>,
                                          val setting: MavenImportingSettings.GeneratedSourcesFolder,
                                          val type: JpsModuleSourceRootType<*>) {
    fun addAnnotationFolder(dir: File) {
      when (setting) {
        SUBFOLDER, AUTODETECT -> addIfDirectoryExists(dir, true)
        else -> {}
      }
    }

    fun collectGeneratedFolders(targetDir: File, annotationFoldersToSkip: Set<File>) {
      fun addAllSubDirs(dir: File) = dir.listFiles()?.forEach { addGeneratedSources(it, annotationFoldersToSkip) }

      when (setting) {
        GENERATED_SOURCE_FOLDER -> addGeneratedSources(targetDir, annotationFoldersToSkip)
        SUBFOLDER -> addAllSubDirs(targetDir)
        AUTODETECT -> {
          for (it in JavaSourceRootDetectionUtil.suggestRoots(targetDir)) {
            val suggestedDir = it.directory
            addGeneratedSources(suggestedDir, annotationFoldersToSkip)

            val suggestedRootPointAtTargetDir = FileUtil.filesEqual(suggestedDir, targetDir)
            if (suggestedRootPointAtTargetDir) return
          }
          addAllSubDirs(targetDir)
        }
        IGNORE -> {}
      }
    }

    private fun addGeneratedSources(dir: File, foldersToSkip: Set<File>) {
      if (dir !in foldersToSkip) {
        addIfDirectoryExists(dir)
      }
    }

    private fun addIfDirectoryExists(dir: File, isAnnotationFolder: Boolean = false) {
      if (dir.isDirectory) {
        result.add(ContentRootCollector.GeneratedSourceFolder(dir.path, type, isAnnotationFolder))
      }
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
