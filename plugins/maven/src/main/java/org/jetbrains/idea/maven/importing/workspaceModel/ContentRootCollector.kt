// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer

object ContentRootCollector {
  sealed class FolderInfo(val path: String, internal val rank: Int) : Comparable<FolderInfo> {
    override fun compareTo(other: FolderInfo): Int {
      val result = FileUtil.comparePaths(path, other.path)
      if (result != 0) return result
      return Comparing.compare(rank, other.rank)
    }

    override fun toString(): String {
      return "${javaClass.simpleName}(path='$path')"
    }
  }

  abstract class BaseSourceFolderInfo(path: String, val type: JpsModuleSourceRootType<*>, rank: Int) : FolderInfo(path, rank) {
    val rootType
      get() = when (type) {
        JavaSourceRootType.SOURCE -> JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID
        JavaSourceRootType.TEST_SOURCE -> JpsModuleRootModelSerializer.JAVA_TEST_ROOT_TYPE_ID
        JavaResourceRootType.RESOURCE -> JpsJavaModelSerializerExtension.JAVA_RESOURCE_ROOT_ID
        JavaResourceRootType.TEST_RESOURCE -> JpsJavaModelSerializerExtension.JAVA_TEST_RESOURCE_ROOT_ID
        else -> error("$type not match to maven root item")
      }


    override fun compareTo(other: FolderInfo): Int {
      val result = super.compareTo(other)
      if (result != 0 || other !is BaseSourceFolderInfo) return result
      return Comparing.compare(rootTypeRank, other.rootTypeRank)
    }

    fun isResource() = JpsJavaModelSerializerExtension.JAVA_RESOURCE_ROOT_ID == rootType
                       || JpsJavaModelSerializerExtension.JAVA_TEST_RESOURCE_ROOT_ID == rootType

    val rootTypeRank
      get() = when (type) {
        JavaSourceRootType.SOURCE -> 0
        JavaSourceRootType.TEST_SOURCE -> 1
        JavaResourceRootType.RESOURCE -> 2
        JavaResourceRootType.TEST_RESOURCE -> 3
        else -> error("$type not match to maven root item")
      }

    override fun toString(): String {
      return "${javaClass.simpleName}(path='$path', rootType='$rootType')"
    }
  }

  abstract class BaseExcludedFolderInfo(path: String, rank: Int) : FolderInfo(path, rank)
  abstract class BaseGeneratedSourceFolderInfo(path: String, type: JpsModuleSourceRootType<*>, rank: Int)
    : BaseSourceFolderInfo(path, type, rank)

  class RootFolderInfo(path: String) : FolderInfo(path, 0)
  class SourceFolderInfo(path: String, type: JpsModuleSourceRootType<*>) : BaseSourceFolderInfo(path, type, 1)
  class ExcludedFolderWithNoGeneratedSubfoldersInfo(path: String) : BaseExcludedFolderInfo(path, 2)
  class GeneratedSourceFolderInfo(path: String, type: JpsModuleSourceRootType<*>) : BaseGeneratedSourceFolderInfo(path, type, 3)
  class OptionalGeneratedSourceFolderInfo(path: String, type: JpsModuleSourceRootType<*>) : BaseGeneratedSourceFolderInfo(path, type, 4)
  class ExcludedFolderInfo(path: String) : BaseExcludedFolderInfo(path, 5)

  fun collect(contentRoots: List<String>,
              sourceFolders: Map<String, JpsModuleSourceRootType<*>>,
              excludeFolders: List<String>,
              doNotRegisterGeneratedSourcesUnder: List<String>,
              generatedFoldersHolder: GeneratedFoldersHolder?): Collection<ContentRootDataHolder> {

    val foldersData = mutableListOf<FolderInfo>()

    contentRoots.forEach { foldersData.add(RootFolderInfo(it)) }
    sourceFolders.forEach { (path, type) -> foldersData.add(SourceFolderInfo(path, type)) }
    generatedFoldersHolder?.generatedSourceFolders?.forEach {
      foldersData.add(GeneratedSourceFolderInfo(it, JavaSourceRootType.SOURCE))
    }
    generatedFoldersHolder?.generatedTestSourceFolders?.forEach {
      foldersData.add(GeneratedSourceFolderInfo(it, JavaSourceRootType.TEST_SOURCE))
    }
    generatedFoldersHolder?.optionalGeneratedFolders?.forEach {
      foldersData.add(OptionalGeneratedSourceFolderInfo(it, JavaSourceRootType.SOURCE))
    }
    generatedFoldersHolder?.optionalGeneratedTestFolders?.forEach {
      foldersData.add(OptionalGeneratedSourceFolderInfo(it, JavaSourceRootType.TEST_SOURCE))
    }
    excludeFolders.forEach { foldersData.add(ExcludedFolderInfo(it)) }
    doNotRegisterGeneratedSourcesUnder.forEach { foldersData.add(ExcludedFolderWithNoGeneratedSubfoldersInfo(it)) }

    foldersData.sort()

    val result = mutableListOf<ContentRootDataHolder>()

    foldersData.forEach { curr ->
      // 1. ADD CONTENT ROOT, IF NEEDED:
      var nearestRoot = result.lastOrNull()
      if (nearestRoot != null && FileUtil.isAncestor(nearestRoot.path, curr.path, false)) {
        if (curr is RootFolderInfo) {
          // don't add nested content roots
          return@forEach
        }

        if (curr is ExcludedFolderInfo && FileUtil.pathsEqual(nearestRoot.path, curr.path)) {
          // don't add exclude that points at the root
          return@forEach
        }
      }
      else {
        if (curr is ExcludedFolderInfo) {
          // don't add root when there is only an exclude folder under it
          return@forEach
        }
        nearestRoot = ContentRootDataHolder(curr.path)
        result.add(nearestRoot)
      }

      // 2. MERGE SUBFOLDERS:
      val prev = nearestRoot.folders.lastOrNull()
      if (prev != null && FileUtil.isAncestor(prev.path, curr.path, false)) {
        if (prev is SourceFolderInfo && curr is BaseSourceFolderInfo) {
          // don't add sub source folders
          return@forEach
        }
        else if (prev is BaseSourceFolderInfo && curr is OptionalGeneratedSourceFolderInfo) {
          // don't add optional generated folder under another source folder (including generated)
          return@forEach
        }
        else if (prev is BaseGeneratedSourceFolderInfo && curr is BaseSourceFolderInfo) {
          // don't add generated folder when there are sub source folder
          nearestRoot.folders.removeLast()
        }
        else if (prev is ExcludedFolderWithNoGeneratedSubfoldersInfo && curr is BaseGeneratedSourceFolderInfo) {
          // don't add generated folders under corresponding exclude folders
          return@forEach
        }
        else if (prev.rank == curr.rank) {
          // merge other subfolders of the same type
          return@forEach
        }
      }

      // 3. REGISTER FOLDER UNDER THE ROOT
      nearestRoot.folders.add(curr)
    }

    return result
  }
}

class GeneratedFoldersHolder(val generatedSourceFolders: List<String>,
                             val generatedTestSourceFolders: List<String>,
                             val optionalGeneratedFolders: List<String> = emptyList(),
                             val optionalGeneratedTestFolders: List<String> = emptyList()) {
  fun toMain() = GeneratedFoldersHolder(generatedSourceFolders, emptyList(), optionalGeneratedFolders)
  fun toTest() = GeneratedFoldersHolder(emptyList(), generatedTestSourceFolders, emptyList(), optionalGeneratedTestFolders)
}

class ContentRootDataHolder(val path: String) {
  val folders = mutableListOf<ContentRootCollector.FolderInfo>()

  override fun toString(): String {
    return "ContentRootDataHolder(contentRoot='$path')"
  }
}
