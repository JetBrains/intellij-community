// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.workspace

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer
import java.util.*

object ContentRootCollector {

  @JvmStatic
  fun collect(baseContentRoot: String,
              folderItemMap: Map<String, JpsModuleSourceRootType<*>>,
              excludedFolders: List<String>,
              generatedFoldersHolder: GeneratedFoldersHolder?) =
    collect(baseContentRoot, folderItemMap, excludedFolders, generatedFoldersHolder, false)

  @JvmStatic
  fun collect(baseContentRoot: String,
              folderItemMap: Map<String, JpsModuleSourceRootType<*>>,
              excludedFolders: List<String>,
              generatedFoldersHolder: GeneratedFoldersHolder?,
              createContentRootForTarget: Boolean): Collection<ContentRootDataHolder> {
    val sortedPotentialContentRootSet = TreeSet { path1: String, path2: String -> FileUtil.comparePaths(path1, path2) }

    sortedPotentialContentRootSet.add(baseContentRoot)
    folderItemMap.forEach { sortedPotentialContentRootSet.add(it.key) }

    val contentRootDataHolderByPath = mutableMapOf<String, ContentRootDataHolder>()

    for (entry in folderItemMap) {
      for (contentRootPath in sortedPotentialContentRootSet) {
        if (FileUtil.isAncestor(contentRootPath, entry.key, false)) {
          val contentRootDataHolder = contentRootDataHolderByPath.getOrPut(contentRootPath) { ContentRootDataHolder(contentRootPath) }
          contentRootDataHolder.sourceFolders.add(SourceFolder(entry.key, entry.value))
          break
        }
      }
    }

    for (folder in excludedFolders) {
      for (contentRootPath in sortedPotentialContentRootSet) {
        if (FileUtil.isAncestor(contentRootPath, folder, false)) {
          if (createContentRootForTarget) {
            val contentRootDataHolder = contentRootDataHolderByPath.getOrPut(contentRootPath) { ContentRootDataHolder(contentRootPath) }
            contentRootDataHolder.excludedPaths.add(folder)
          }
          else {
            contentRootDataHolderByPath.get(contentRootPath)?.excludedPaths?.add(folder)
          }
          break
        }
      }
    }

    addGeneratedContentRoots(generatedFoldersHolder, sortedPotentialContentRootSet, contentRootDataHolderByPath)

    return contentRootDataHolderByPath.values
  }

  private fun addGeneratedContentRoots(generatedFoldersHolder: GeneratedFoldersHolder?,
                                       sortedPotentialContentRootSet: TreeSet<String>,
                                       contentRootDataHolderByPath: MutableMap<String, ContentRootDataHolder>) {
    if (generatedFoldersHolder == null) return

    if (generatedFoldersHolder.annotationProcessorDirectory != null) {
      val folder = generatedFoldersHolder.annotationProcessorDirectory
      val contentRootHolder = getGeneratedContentRootDataHolder(sortedPotentialContentRootSet, folder, contentRootDataHolderByPath)
      contentRootHolder.annotationProcessorFolders.add(SourceFolder(folder, JavaSourceRootType.SOURCE))
    }
    if (generatedFoldersHolder.annotationProcessorTestDirectory != null) {
      val folder = generatedFoldersHolder.annotationProcessorTestDirectory
      val contentRootHolder = getGeneratedContentRootDataHolder(sortedPotentialContentRootSet, folder, contentRootDataHolderByPath)
      contentRootHolder.annotationProcessorFolders.add(SourceFolder(folder, JavaSourceRootType.TEST_SOURCE))
    }
    if (generatedFoldersHolder.generatedSourceFolder != null) {
      val folder = generatedFoldersHolder.generatedSourceFolder
      val contentRootHolder = getGeneratedContentRootDataHolder(sortedPotentialContentRootSet, folder, contentRootDataHolderByPath)
      contentRootHolder.generatedFolders.add(SourceFolder(folder, JavaSourceRootType.SOURCE))
    }
    if (generatedFoldersHolder.generatedTestSourceFolder != null) {
      val folder = generatedFoldersHolder.generatedTestSourceFolder
      val contentRootHolder = getGeneratedContentRootDataHolder(sortedPotentialContentRootSet, folder, contentRootDataHolderByPath)
      contentRootHolder.generatedFolders.add(SourceFolder(folder, JavaSourceRootType.TEST_SOURCE))
    }
  }

  private fun getGeneratedContentRootDataHolder(
    sortedPotentialContentRootSet: TreeSet<String>,
    folder: String,
    contentRootDataHolderByPath: MutableMap<String, ContentRootDataHolder>): ContentRootDataHolder {

    var contentRootHolder: ContentRootDataHolder? = null;
    for (contentRootPath in sortedPotentialContentRootSet) {
      if (FileUtil.isAncestor(contentRootPath, folder, false)) {
        contentRootHolder = contentRootDataHolderByPath.get(contentRootPath)
        break
      }
    }
    if (contentRootHolder == null) {
      contentRootHolder = ContentRootDataHolder(folder)
      contentRootDataHolderByPath.put(folder, contentRootHolder)
    }
    return contentRootHolder
  }
}

class GeneratedFoldersHolder(val annotationProcessorDirectory: String?,
                             val annotationProcessorTestDirectory: String?,
                             val generatedSourceFolder: String?,
                             val generatedTestSourceFolder: String?) {
  fun toMain() = GeneratedFoldersHolder(annotationProcessorDirectory, null, generatedSourceFolder, null)
  fun toTest() = GeneratedFoldersHolder(null, annotationProcessorTestDirectory, null, generatedTestSourceFolder)
}

class ContentRootDataHolder(val contentRoot: String) {
  val sourceFolders = mutableListOf<SourceFolder>()
  val excludedPaths = mutableListOf<String>()
  val annotationProcessorFolders = mutableListOf<SourceFolder>()
  val generatedFolders = mutableListOf<SourceFolder>()

  override fun toString(): String {
    return "ContentRootDataHolder(contentRoot='$contentRoot')"
  }
}

class SourceFolder(val path: String, sourceRootType: JpsModuleSourceRootType<*>) {
  val rootType = getRootType(sourceRootType)

  fun isResource() = JpsJavaModelSerializerExtension.JAVA_RESOURCE_ROOT_ID == rootType
                     || JpsJavaModelSerializerExtension.JAVA_TEST_RESOURCE_ROOT_ID == rootType

  private fun getRootType(sourceRootType: JpsModuleSourceRootType<*>): String {
    return when (sourceRootType) {
      JavaSourceRootType.SOURCE -> JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID
      JavaSourceRootType.TEST_SOURCE -> JpsModuleRootModelSerializer.JAVA_TEST_ROOT_TYPE_ID
      JavaResourceRootType.RESOURCE -> JpsJavaModelSerializerExtension.JAVA_RESOURCE_ROOT_ID
      JavaResourceRootType.TEST_RESOURCE -> JpsJavaModelSerializerExtension.JAVA_TEST_RESOURCE_ROOT_ID
      else -> error("$sourceRootType not match to maven root item")
    }
  }

  override fun toString(): String {
    return "SourceFolder(path='$path', rootType='$rootType')"
  }


}