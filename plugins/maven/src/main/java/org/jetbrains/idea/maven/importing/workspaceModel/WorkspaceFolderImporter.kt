// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil
import com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.java.workspace.entities.javaResourceRoots
import com.intellij.java.workspace.entities.javaSourceRoots
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.containers.FileCollectionFactory
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_TEST_ROOT_ENTITY_TYPE_ID
import org.jetbrains.idea.maven.importing.MavenImportUtil.getAnnotationProcessorDirectory
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
import java.io.File
import java.util.stream.Stream

internal class WorkspaceFolderImporter(
  private val builder: MutableEntityStorage,
  private val virtualFileUrlManager: VirtualFileUrlManager,
  private val importingSettings: MavenImportingSettings,
  private val importingContext: FolderImportingContext,
  private val workspaceConfigurators: List<MavenWorkspaceConfigurator>,
  private val project: Project
) {

  fun createContentRoots(
    mavenProject: MavenProject, moduleType: StandardMavenModuleType, module: ModuleEntity,
    stats: WorkspaceImportStats,
  ): OutputFolders {
    val cachedFolders = importingContext.projectToCachedFolders.getOrPut(mavenProject) {
      collectMavenFolders(mavenProject, stats)
    }

    val outputFolders = OutputFolders(cachedFolders.outputPath, cachedFolders.testOutputPath)

    // do not create source roots in additional <compileSourceRoots> modules
    if (moduleType == StandardMavenModuleType.MAIN_ONLY_ADDITIONAL) return outputFolders

    val allFolders = mutableListOf<ContentRootCollector.ImportedFolder>()
    addContentRoot(cachedFolders, allFolders, isSharedSourceSupportEnabled(project))
    addCachedFolders(moduleType, cachedFolders, allFolders)

    for (root in ContentRootCollector.collect(allFolders)) {
      val excludes = root.excludeFolders
        .map { exclude -> virtualFileUrlManager.getOrCreateFromUrl(VfsUtilCore.pathToUrl(exclude.path)) }
        .map { ExcludeUrlEntity(it, module.entitySource) }
      val newContentRootEntity = ContentRootEntity(virtualFileUrlManager.getOrCreateFromUrl(VfsUtilCore.pathToUrl(root.path)),
                                                   emptyList(), module.entitySource) {
        this.excludedUrls = excludes
      }
      root.sourceFolders.forEach { folder ->
        registerSourceRootFolder(newContentRootEntity, folder)
      }
      builder.modifyModuleEntity(module) {
        this.contentRoots += newContentRootEntity
      }
    }

    return outputFolders
  }

  private fun addContentRoot(cachedFolders: CachedProjectFolders,
                             allFolders: MutableList<ContentRootCollector.ImportedFolder>,
                             duplicatesAreAllowed: Boolean = false) {
    val contentRoot = cachedFolders.projectContentRootPath

    if (!duplicatesAreAllowed) {
      // make sure we don't have overlapping content roots in different modules
      val alreadyRegisteredRoot = importingContext.alreadyRegisteredContentRoots.contains(contentRoot)
      if (alreadyRegisteredRoot) return
    }

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
                        StandardMavenModuleType.AGGREGATOR -> cachedFolders.folders.filter { it is ContentRootCollector.ExcludedFolder }
                        else -> cachedFolders.folders
                      })
  }

  private fun registerSourceRootFolder(contentRootEntity: ContentRootEntity.Builder,
                                       folder: ContentRootCollector.SourceFolderResult) {
    val rootTypeId = when (folder.type) {
      JavaSourceRootType.SOURCE -> JAVA_SOURCE_ROOT_ENTITY_TYPE_ID
      JavaSourceRootType.TEST_SOURCE -> JAVA_TEST_ROOT_ENTITY_TYPE_ID
      JavaResourceRootType.RESOURCE -> JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID
      JavaResourceRootType.TEST_RESOURCE -> JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID
      else -> error("${folder.type} doesn't  match to maven root item")
    }

    val sourceRootEntity = SourceRootEntity(virtualFileUrlManager.getOrCreateFromUrl(VfsUtilCore.pathToUrl(folder.path)), rootTypeId,
                                                              contentRootEntity.entitySource)

    val isResource = JAVA_RESOURCE_ROOT_ENTITY_TYPE_ID == rootTypeId
                     || JAVA_TEST_RESOURCE_ROOT_ENTITY_TYPE_ID == rootTypeId

    if (isResource) {
      sourceRootEntity.javaResourceRoots += JavaResourceRootPropertiesEntity(folder.isGenerated, "", sourceRootEntity.entitySource)
    }
    else {
      sourceRootEntity.javaSourceRoots += JavaSourceRootPropertiesEntity(folder.isGenerated, "", sourceRootEntity.entitySource)
    }

    contentRootEntity.sourceRoots += sourceRootEntity
  }

  private fun collectMavenFolders(mavenProject: MavenProject, stats: WorkspaceImportStats): CachedProjectFolders {
    val folders = mutableListOf<ContentRootCollector.ImportedFolder>()

    val configuratorContext = object : MavenWorkspaceConfigurator.FoldersContext {
      override val mavenProject = mavenProject
    }


    collectSourceFolders(mavenProject, folders, configuratorContext, stats)
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

    collectExcludedFoldersFromConfigurators(stats, configuratorContext, folders, mavenProject)

    return CachedProjectFolders(mavenProject.directory, outputPath, testOutputPath, folders)
  }

  private fun collectExcludedFoldersFromConfigurators(stats: WorkspaceImportStats,
                                                      configuratorContext: MavenWorkspaceConfigurator.FoldersContext,
                                                      folders: MutableList<ContentRootCollector.ImportedFolder>,
                                                      mavenProject: MavenProject) {
    for (each in workspaceConfigurators) {
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
                                   stats: WorkspaceImportStats) {
    fun toAbsolutePath(path: String) = MavenUtil.toPath(mavenProject, path).path

    mavenProject.sources.forEach { result.add(ContentRootCollector.SourceFolder(it, JavaSourceRootType.SOURCE)) }
    mavenProject.resources.forEach { result.add(ContentRootCollector.SourceFolder(it.directory, JavaResourceRootType.RESOURCE)) }

    mavenProject.testSources.forEach { result.add(ContentRootCollector.SourceFolder(it, JavaSourceRootType.TEST_SOURCE)) }
    mavenProject.testResources.forEach { result.add(ContentRootCollector.SourceFolder(it.directory, JavaResourceRootType.TEST_RESOURCE)) }


    for (each in workspaceConfigurators) {
      stats.recordConfigurator(each, MavenImportCollector.COLLECT_FOLDERS_DURATION_MS) {
        try {
          each.getAdditionalFolders(configuratorContext)
        }
        catch (e: Exception) {
          MavenLog.LOG.error("Exception in MavenWorkspaceConfigurator.getAdditionalSourceFolders, skipping it.", e)
          Stream.empty()
        }
      }.forEach {
        val path = toAbsolutePath(it.path)
        val folder = when (it.type) {
          MavenWorkspaceConfigurator.FolderType.SOURCE -> ContentRootCollector.SourceFolder(path, JavaSourceRootType.SOURCE)
          MavenWorkspaceConfigurator.FolderType.TEST_SOURCE -> ContentRootCollector.SourceFolder(path, JavaSourceRootType.TEST_SOURCE)
          MavenWorkspaceConfigurator.FolderType.RESOURCE -> ContentRootCollector.SourceFolder(path, JavaResourceRootType.RESOURCE)
          MavenWorkspaceConfigurator.FolderType.TEST_RESOURCE -> ContentRootCollector.SourceFolder(path, JavaResourceRootType.TEST_RESOURCE)
          MavenWorkspaceConfigurator.FolderType.GENERATED_SOURCE -> ContentRootCollector.GeneratedSourceFolder(path, JavaSourceRootType.SOURCE)
          MavenWorkspaceConfigurator.FolderType.GENERATED_TEST_SOURCE -> ContentRootCollector.GeneratedSourceFolder(path, JavaSourceRootType.TEST_SOURCE)
        }
        result.add(folder)
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
    val folders: List<ContentRootCollector.ImportedFolder>,
  )

  data class OutputFolders(val outputPath: String, val testOutputPath: String)
}
