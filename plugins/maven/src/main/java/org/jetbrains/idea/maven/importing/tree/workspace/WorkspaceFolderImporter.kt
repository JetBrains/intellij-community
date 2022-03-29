// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.workspace

import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.importing.MavenFoldersImporter
import org.jetbrains.idea.maven.importing.tree.MavenModuleType
import org.jetbrains.idea.maven.project.MavenImportingSettings
import org.jetbrains.idea.maven.project.MavenImportingSettings.GeneratedSourcesFolder.*
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import java.io.File
import java.nio.file.Path

class WorkspaceFolderImporter(
  private val builder: WorkspaceEntityStorageBuilder,
  private val virtualFileUrlManager: VirtualFileUrlManager,
  private val importingSettings: MavenImportingSettings) {

  fun createContentRoots(module: ModuleEntity,
                         importData: MavenModuleImportData,
                         excludedFolders: List<String>,
                         generatedFolders: GeneratedFoldersHolder?) =
    createContentRoots(module, importData, excludedFolders, generatedFolders, false)

  fun createContentRoots(module: ModuleEntity,
                         importData: MavenModuleImportData,
                         excludedFolders: List<String>,
                         generatedFolders: GeneratedFoldersHolder?,
                         createContentRootForTarget: Boolean) {
    val contentRootDataHolders = getContentRootsData(importData, excludedFolders, generatedFolders, createContentRootForTarget)

    for (dataHolder in contentRootDataHolders) {
      val contentRootEntity = builder
        .addContentRootEntity(virtualFileUrlManager.fromPath(dataHolder.contentRoot),
                              dataHolder.excludedPaths.map { virtualFileUrlManager.fromPath(it) },
                              emptyList(), module)

      for (sourceFolder in dataHolder.sourceFolders) {
        addSourceRootFolder(contentRootEntity, sourceFolder)
      }

      for (folder in dataHolder.annotationProcessorFolders) {
        addGeneratedJavaSourceFolder(folder.path, folder.rootType, contentRootEntity)
      }

      for (folder in dataHolder.generatedFolders) {
        configGeneratedSourceFolder(File(folder.path), folder.rootType, contentRootEntity)
      }

    }
  }

  private fun addSourceRootFolder(contentRootEntity: ContentRootEntity,
                                  sourceFolder: SourceFolder) {
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

  private fun getContentRootsData(importData: MavenModuleImportData,
                                  excludedFolders: List<String>,
                                  generatedFoldersHolder: GeneratedFoldersHolder?,
                                  createContentRootForTarget: Boolean): Collection<ContentRootDataHolder> {
    val baseContentRoot = getBaseContentRoot(importData)
    val folderItemMap = getFolderItemMap(importData)
    return ContentRootCollector
      .collect(baseContentRoot, folderItemMap, excludedFolders, generatedFoldersHolder, createContentRootForTarget)
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

  private fun addGeneratedJavaSourceFolder(path: String, rootType: String, contentRootEntity: ContentRootEntity) {
    if (File(path).list().isNullOrEmpty()) return

    val url = virtualFileUrlManager.fromPath(path)
    if (contentRootEntity.sourceRoots.any { it.url == url }) return

    val sourceRootEntity = builder.addSourceRootEntity(contentRootEntity, url,
                                                       rootType,
                                                       contentRootEntity.entitySource)
    builder.addJavaSourceRootEntity(sourceRootEntity, true, "")
  }

  private fun configGeneratedSourceFolder(targetDir: File, rootType: String, contentRootEntity: ContentRootEntity) {
    when (importingSettings.generatedSourcesFolder) {
      GENERATED_SOURCE_FOLDER -> addGeneratedJavaSourceFolder(targetDir.path, rootType, contentRootEntity)
      SUBFOLDER -> addAllSubDirsAsGeneratedSources(targetDir, rootType, contentRootEntity)
      AUTODETECT -> {
        val sourceRoots = JavaSourceRootDetectionUtil.suggestRoots(targetDir)
        for (root in sourceRoots) {
          if (FileUtil.filesEqual(targetDir, root.directory)) {
            addGeneratedJavaSourceFolder(targetDir.path, rootType, contentRootEntity)
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
      addGeneratedJavaSourceFolder(dir.path, rootType, contentRootEntity)
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
