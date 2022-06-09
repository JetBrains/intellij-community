// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType

object ContentRootCollector {
  fun collect(folders: List<Folder>): Collection<Result> {
    val result = mutableListOf<Result>()

    folders.sorted().forEach { curr ->
      // 1. ADD CONTENT ROOT, IF NEEDED:
      var nearestRoot = result.lastOrNull()
      if (nearestRoot != null && FileUtil.isAncestor(nearestRoot.path, curr.path, false)) {
        if (curr is ContentRootFolder) {
          // don't add nested content roots
          return@forEach
        }

        if (curr is ExcludedFolder && FileUtil.pathsEqual(nearestRoot.path, curr.path)) {
          // don't add exclude that points at the root
          return@forEach
        }
      }
      else {
        if (curr is ExcludedFolder) {
          // don't add root when there is only an exclude folder under it
          return@forEach
        }
        nearestRoot = Result(curr.path)
        result.add(nearestRoot)
      }

      // 2. MERGE SUBFOLDERS:
      val prev = nearestRoot.folders.lastOrNull()
      if (prev != null && FileUtil.isAncestor(prev.path, curr.path, false)) {
        if (prev is SourceFolder && curr is UserOrGeneratedSourceFolder) {
          // don't add sub source folders
          return@forEach
        }
        else if (prev is UserOrGeneratedSourceFolder && curr is OptionalGeneratedSourceFolder) {
          // don't add optional generated folder under another source folder (including generated)
          return@forEach
        }
        else if (prev is BaseGeneratedSourceFolder && curr is UserOrGeneratedSourceFolder) {
          // don't add generated folder when there are sub source folder
          nearestRoot.folders.removeLast()
        }
        else if (prev is ExcludedFolderAndPreventGeneratedSubfolders && curr is UserOrGeneratedSourceFolder) {
          // don't add source folders under corresponding exclude folders
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

    // 4. Now, we need a second pass over the merged folders, to remove nested exclude folders.
    //    Couldn't do it during the first pass, since exclude folders have different properties (preventing subfolders),
    //    which need to be taken into account during the first pass.
    val rootIterator = result.iterator()
    while (rootIterator.hasNext()) {
      val root = rootIterator.next()

      val folderIterator = root.folders.iterator()
      var prev: Folder? = null
      while (folderIterator.hasNext()) {
        val curr = folderIterator.next()
        if (prev is BaseExcludedFolder && curr is BaseExcludedFolder
            && FileUtil.isAncestor(prev.path, curr.path, false)) {
          folderIterator.remove()
        }
        else {
          prev = curr
        }
      }
    }

    return result
  }

  sealed class Folder(val path: String, internal val rank: Int) : Comparable<Folder> {
    override fun compareTo(other: Folder): Int {
      val result = FileUtil.comparePaths(path, other.path)
      if (result != 0) return result
      return Comparing.compare(rank, other.rank)
    }

    override fun toString(): String {
      return path
    }
  }

  abstract class UserOrGeneratedSourceFolder(path: String, val type: JpsModuleSourceRootType<*>, rank: Int) : Folder(path, rank) {
    override fun compareTo(other: Folder): Int {
      val result = super.compareTo(other)
      if (result != 0 || other !is UserOrGeneratedSourceFolder) return result
      return Comparing.compare(rootTypeRank, other.rootTypeRank)
    }

    val rootTypeRank
      get() = when (type) {
        JavaSourceRootType.SOURCE -> 0
        JavaSourceRootType.TEST_SOURCE -> 1
        JavaResourceRootType.RESOURCE -> 2
        JavaResourceRootType.TEST_RESOURCE -> 3
        else -> error("$type not match to maven root item")
      }

    override fun toString(): String {
      return "$path rootType='$type'"
    }
  }

  abstract class BaseExcludedFolder(path: String, rank: Int) : Folder(path, rank)
  abstract class BaseGeneratedSourceFolder(path: String, type: JpsModuleSourceRootType<*>, rank: Int)
    : UserOrGeneratedSourceFolder(path, type, rank)

  class ContentRootFolder(path: String) : Folder(path, 0)
  class SourceFolder(path: String, type: JpsModuleSourceRootType<*>) : UserOrGeneratedSourceFolder(path, type, 1)
  class ExcludedFolderAndPreventGeneratedSubfolders(path: String) : BaseExcludedFolder(path, 2)
  class ExplicitGeneratedSourceFolder(path: String, type: JpsModuleSourceRootType<*>) : BaseGeneratedSourceFolder(path, type, 3)
  class OptionalGeneratedSourceFolder(path: String, type: JpsModuleSourceRootType<*>) : BaseGeneratedSourceFolder(path, type, 4)
  class ExcludedFolder(path: String) : BaseExcludedFolder(path, 5)

  class Result(val path: String) {
    val folders = mutableListOf<Folder>()

    override fun toString(): String {
      return path
    }
  }
}
