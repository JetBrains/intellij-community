// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType

object ContentRootCollector {
  fun collect(folders: List<ImportedFolder>): Collection<ContentRootResult> {
    class ContentRootWithFolders(val path: String, val folders: MutableList<ImportedFolder> = mutableListOf())

    val result = mutableListOf<ContentRootWithFolders>()

    folders.sorted().forEach { curr ->
      // 1. ADD CONTENT ROOT, IF NEEDED:
      var nearestRoot = result.lastOrNull()
      if (nearestRoot != null && FileUtil.isAncestor(nearestRoot.path, curr.path, false)) {
        if (curr is ProjectRootFolder) {
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
        nearestRoot = ContentRootWithFolders(curr.path)
        result.add(nearestRoot)
      }

      // 2. MERGE DUPLICATE PATHS
      val prev = nearestRoot.folders.lastOrNull()
      if (prev != null && FileUtil.pathsEqual(prev.path, curr.path)) {
        if (prev.rank <= curr.rank) {
          return@forEach
        }
      }

      // 3. MERGE SUBFOLDERS:
      if (prev != null && FileUtil.isAncestor(prev.path, curr.path, true)) {
        if (prev is SourceFolder && curr is UserOrGeneratedSourceFolder) {
          // don't add sub source folders
          return@forEach
        }
        else if (prev is GeneratedSourceFolder && curr is UserOrGeneratedSourceFolder) {
          // don't add generated folder when there are sub source folder
          nearestRoot.folders.removeLast()
        }
        else if (prev is ExcludedFolderAndPreventSubfolders && curr is UserOrGeneratedSourceFolder) {
          // don't add source folders under corresponding exclude folders
          return@forEach
        }
        else if (prev.rank == curr.rank) {
          // merge other subfolders of the same type
          return@forEach
        }
      }

      // 4. REGISTER FOLDER UNDER THE ROOT
      nearestRoot.folders.add(curr)
    }

    // Now, we need a second pass over the merged folders, to remove nested exclude folders.
    //  Couldn't do it during the first pass, since exclude folders have different properties (preventing subfolders),
    //  which need to be taken into account during the first pass.
    val rootIterator = result.iterator()
    while (rootIterator.hasNext()) {
      val root = rootIterator.next()

      val folderIterator = root.folders.iterator()
      var prev: ImportedFolder? = null
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

    return result.map { root ->
      val sourceFolders = root.folders.asSequence().filterIsInstance<UserOrGeneratedSourceFolder>().map { folder ->
        SourceFolderResult(folder.path, folder.type, folder is GeneratedSourceFolder)
      }
      val excludeFolders = root.folders.asSequence().filterIsInstance<BaseExcludedFolder>().map { folder ->
        ExcludedFolderResult(folder.path)
      }
      ContentRootResult(root.path, sourceFolders.toList(), excludeFolders.toList())
    }
  }

  sealed class ImportedFolder(path: String, internal val rank: Int) : Comparable<ImportedFolder> {
    val path: String = FileUtil.toCanonicalPath(path)

    override fun compareTo(other: ImportedFolder): Int {
      val result = FileUtil.comparePaths(path, other.path)
      if (result != 0) return result
      return Comparing.compare(rank, other.rank)
    }

    override fun toString(): String {
      return path
    }
  }

  abstract class UserOrGeneratedSourceFolder(path: String, val type: JpsModuleSourceRootType<*>, rank: Int) : ImportedFolder(path, rank) {
    override fun compareTo(other: ImportedFolder): Int {
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
      return "$path rootType='${if (type.isForTests) "test" else "main"} ${type.javaClass.simpleName}'"
    }
  }

  abstract class BaseExcludedFolder(path: String, rank: Int) : ImportedFolder(path, rank)
  class ProjectRootFolder(path: String) : ImportedFolder(path, 0)
  class SourceFolder(path: String, type: JpsModuleSourceRootType<*>) : UserOrGeneratedSourceFolder(path, type, 1)
  class ExcludedFolderAndPreventSubfolders(path: String) : BaseExcludedFolder(path, 2)
  class GeneratedSourceFolder(path: String, type: JpsModuleSourceRootType<*>) : UserOrGeneratedSourceFolder(path, type, 3)
  class ExcludedFolder(path: String) : BaseExcludedFolder(path, 4)

  class ContentRootResult(val path: String,
                          val sourceFolders: List<SourceFolderResult>,
                          val excludeFolders: List<ExcludedFolderResult>) {
    override fun toString() = path
  }

  class SourceFolderResult(val path: String, val type: JpsModuleSourceRootType<*>, val isGenerated: Boolean) {
    override fun toString() = "$path ${if (isGenerated) "generated" else ""} rootType='${if (type.isForTests) "test" else "main"}  ${type.javaClass.simpleName}'"
  }

  class ExcludedFolderResult(val path: String) {
    override fun toString() = path
  }
}
