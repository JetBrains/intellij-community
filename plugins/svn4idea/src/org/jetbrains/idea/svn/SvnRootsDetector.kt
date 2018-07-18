// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.svn.SvnUtil.*
import org.jetbrains.idea.svn.api.Url
import java.io.File
import java.util.stream.Collectors.toList

class SvnRootsDetector(private val myVcs: SvnVcs,
                       private val myMapping: SvnFileUrlMappingImpl,
                       private val myNestedCopiesHolder: NestedCopiesHolder) {
  private val myResult = Result()
  private val myRepositoryRoots = RepositoryRoots(myVcs)

  fun detectCopyRoots(roots: Array<VirtualFile>, clearState: Boolean, callback: Runnable) {
    for (vcsRoot in roots) {
      val foundRoots = ForNestedRootChecker(myVcs).getAllNestedWorkingCopies(vcsRoot)

      registerLonelyRoots(vcsRoot, foundRoots)
      registerTopRoots(vcsRoot, foundRoots)
    }

    addNestedRoots(clearState, callback)
  }

  private fun registerLonelyRoots(vcsRoot: VirtualFile, foundRoots: List<Node>) {
    if (foundRoots.isEmpty()) {
      myResult.lonelyRoots.add(vcsRoot)
    }
  }

  private fun registerTopRoots(vcsRoot: VirtualFile, foundRoots: List<Node>) {
    // filter out bad(?) items
    for (foundRoot in foundRoots) {
      val root = RootUrlInfo(foundRoot, SvnFormatSelector.findRootAndGetFormat(foundRoot.ioFile), vcsRoot)

      if (!foundRoot.hasError()) {
        myRepositoryRoots.register(foundRoot.repositoryRootUrl)
        myResult.topRoots.add(root)
      }
      else {
        myResult.errorRoots.add(root)
      }
    }
  }

  private fun addNestedRoots(clearState: Boolean, callback: Runnable) {
    val basicVfRoots = myResult.topRoots.map { it.virtualFile }
    val clManager = ChangeListManager.getInstance(myVcs.project)

    if (clearState) {
      // clear what was reported before (could be for currently-not-existing roots)
      myNestedCopiesHolder.getAndClear()
      VcsDirtyScopeManager.getInstance(myVcs.project).filesDirty(null, basicVfRoots)
    }
    clManager.invokeAfterUpdate(
      {
        val nestedRoots = mutableListOf<RootUrlInfo>()

        for (info in myNestedCopiesHolder.getAndClear()) {
          if (NestedCopyType.external == info.type || NestedCopyType.switched == info.type) {
            val topRoot = findTopRoot(virtualToIoFile(info.file))

            if (topRoot != null) {
              // TODO: Seems that type is not set in ForNestedRootChecker as we could not determine it for sure. Probably, for the case
              // TODO: (or some other cases) when vcs root from settings belongs is in externals of some other working copy upper
              // TODO: the tree (I did not check this). Leave this setter for now.
              topRoot.type = info.type
              continue
            }
            if (!refreshPointInfo(info)) {
              continue
            }
          }
          registerRootUrlFromNestedPoint(info, nestedRoots)
        }

        myResult.topRoots.addAll(nestedRoots)
        putWcDbFilesToVfs(myResult.topRoots)
        myMapping.applyDetectionResult(myResult)

        callback.run()
      }, InvokeAfterUpdateMode.SILENT_CALLBACK_POOLED, null, null)
  }

  private fun putWcDbFilesToVfs(infos: Collection<RootUrlInfo>) {
    if (!SvnVcs.ourListenToWcDb) return

    val wcDbFiles = infos.stream()
      .filter { it.format.isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN) }
      .filter { NestedCopyType.switched != it.type }
      .map { it.ioFile }
      .map { getWcDb(it) }
      .collect(toList())

    LocalFileSystem.getInstance().refreshIoFiles(wcDbFiles)
  }

  private fun registerRootUrlFromNestedPoint(info: NestedCopyInfo, nestedRoots: MutableList<RootUrlInfo>) {
    // TODO: Seems there could be issues if myTopRoots contains nested roots => RootUrlInfo.myRoot could be incorrect
    // TODO: (not nearest ancestor) for new RootUrlInfo
    findAncestorTopRoot(info.file)?.let { topRoot ->
      (info.rootURL ?: myRepositoryRoots.ask(info.url, info.file))?.let { repoRoot ->
        val node = Node(info.file, info.url!!, repoRoot)
        nestedRoots.add(RootUrlInfo(node, info.format, topRoot.root, info.type))
      }
    }
  }

  private fun refreshPointInfo(info: NestedCopyInfo): Boolean {
    // TODO: Here we refresh url, repository url, format because they are not set for some NestedCopies in NestedCopiesBuilder.
    // TODO: For example they are not set for externals. Probably this logic could be moved to NestedCopiesBuilder instead.
    var refreshed = false

    val infoFile = virtualToIoFile(info.file)
    val svnStatus = getStatus(myVcs, infoFile)

    if (svnStatus != null && svnStatus.url != null) {
      info.url = svnStatus.url
      info.format = myVcs.getWorkingCopyFormat(infoFile, false)
      if (svnStatus.repositoryRootURL != null) {
        info.rootURL = svnStatus.repositoryRootURL
      }
      refreshed = true
    }

    return refreshed
  }

  private fun findTopRoot(file: File) = myResult.topRoots.find { FileUtil.filesEqual(it.ioFile, file) }

  private fun findAncestorTopRoot(file: VirtualFile) = myResult.topRoots.find { VfsUtilCore.isAncestor(it.virtualFile, file, true) }

  private class RepositoryRoots(private val myVcs: SvnVcs) {
    private val myRoots = mutableSetOf<Url>()

    fun register(url: Url) {
      myRoots.add(url)
    }

    fun ask(url: Url?, file: VirtualFile): Url? {
      val root = url?.let { myRoots.find { root -> isAncestor(root, it) } }
      // TODO: Seems that RepositoryRoots class should be removed. And necessary repository root should be determined explicitly
      // TODO: using info command.
      return root ?: getRepositoryRoot(myVcs, virtualToIoFile(file))?.also { myRoots.add(it) }
    }
  }

  class Result {
    val lonelyRoots = mutableListOf<VirtualFile>()
    val topRoots = mutableListOf<RootUrlInfo>()
    val errorRoots = mutableListOf<RootUrlInfo>()
  }
}
