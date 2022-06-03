// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer

object ContentRootCollector {
  @JvmStatic
  fun collect(baseContentRoot: String,
              sourceFolders: Map<String, JpsModuleSourceRootType<*>>,
              excludeFolders: List<String>,
              doNotRegisterSourcesUnder: List<String>,
              generatedFoldersHolder: GeneratedFoldersHolder?): Collection<ContentRootDataHolder> {

    data class FolderInfo(val path: String,
                          val type: JpsModuleSourceRootType<*>?,
                          val container: ((ContentRootDataHolder) -> MutableList<SourceFolderData>)?)

    val foldersData = sortedSetOf<FolderInfo>(Comparator { a, b -> FileUtil.comparePaths(a.path, b.path) })
    val alreadyAddedSourcesFolders = mutableSetOf<String>()

    fun addToSortedFoldersData(path: String,
                               type: JpsModuleSourceRootType<*>?,
                               container: ((ContentRootDataHolder) -> MutableList<SourceFolderData>)?) {
      if (alreadyAddedSourcesFolders.add(path)) {
        if (doNotRegisterSourcesUnder.none { FileUtil.isAncestor(it, path, false) }) {
          foldersData.add(FolderInfo(path, type, container))
        }
      }
    }

    sourceFolders.forEach { (path, type) -> addToSortedFoldersData(path, type) { root -> root.sourceFolders } }
    generatedFoldersHolder?.apply {
      annotationProcessorDirectory?.let {
        addToSortedFoldersData(it, JavaSourceRootType.SOURCE) { root -> root.annotationProcessorFolders }
      }
      annotationProcessorTestDirectory?.let {
        addToSortedFoldersData(it, JavaSourceRootType.TEST_SOURCE) { root -> root.annotationProcessorFolders }
      }

      generatedSourceFolder?.let { addToSortedFoldersData(it, JavaSourceRootType.SOURCE) { root -> root.generatedFolders } }
      generatedTestSourceFolder?.let { addToSortedFoldersData(it, JavaSourceRootType.TEST_SOURCE) { root -> root.generatedFolders } }
    }
    addToSortedFoldersData(baseContentRoot, null, null)

    val contentRoots = mutableListOf<ContentRootDataHolder>()

    foldersData.forEach { (path, type, container) ->
      var addToRoot = contentRoots.lastOrNull()
      if (addToRoot == null || !FileUtil.isAncestor(addToRoot.contentRoot, path, false)) {
        addToRoot = ContentRootDataHolder(path)
        contentRoots.add(addToRoot)
      }
      container?.invoke(addToRoot)?.add(SourceFolderData(path, type!!))
    }

    for (exclude in excludeFolders.asSequence() + doNotRegisterSourcesUnder.asSequence()) {
      for (eachRoot in contentRoots) {
        if (FileUtil.isAncestor(eachRoot.contentRoot, exclude, true)) {
          eachRoot.excludedPaths.add(exclude)
          break
        }
      }
    }

    return contentRoots
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
  val sourceFolders = mutableListOf<SourceFolderData>()
  val excludedPaths = mutableListOf<String>()
  val annotationProcessorFolders = mutableListOf<SourceFolderData>()
  val generatedFolders = mutableListOf<SourceFolderData>()

  override fun toString(): String {
    return "ContentRootDataHolder(contentRoot='$contentRoot')"
  }
}

class SourceFolderData(val path: String, sourceRootType: JpsModuleSourceRootType<*>) {
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