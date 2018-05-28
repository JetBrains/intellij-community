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
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.ContainerUtil.map
import org.jetbrains.idea.svn.SvnUtil.isAncestor
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
    val basicVfRoots = map<RootUrlInfo, VirtualFile>(myResult.topRoots) { it.virtualFile }
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
      .filter { info -> info.format.isOrGreater(WorkingCopyFormat.ONE_DOT_SEVEN) }
      .filter { info -> NestedCopyType.switched != info.type }
      .map { it.ioFile }
      .map { SvnUtil.getWcDb(it) }
      .collect(toList())

    LocalFileSystem.getInstance().refreshIoFiles(wcDbFiles)
  }

  private fun registerRootUrlFromNestedPoint(info: NestedCopyInfo, nestedRoots: MutableList<RootUrlInfo>) {
    // TODO: Seems there could be issues if myTopRoots contains nested roots => RootUrlInfo.myRoot could be incorrect
    // TODO: (not nearest ancestor) for new RootUrlInfo
    val topRoot = findAncestorTopRoot(info.file)

    if (topRoot != null) {
      var repoRoot = info.rootURL
      repoRoot = if (repoRoot == null) myRepositoryRoots.ask(info.url, info.file) else repoRoot
      if (repoRoot != null) {
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
    val svnStatus = SvnUtil.getStatus(myVcs, infoFile)

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

  private fun findTopRoot(file: File): RootUrlInfo? {
    return ContainerUtil.find(myResult.topRoots) { topRoot -> FileUtil.filesEqual(topRoot.ioFile, file) }
  }

  private fun findAncestorTopRoot(file: VirtualFile): RootUrlInfo? {
    return ContainerUtil.find(myResult.topRoots) { topRoot -> VfsUtilCore.isAncestor(topRoot.virtualFile, file, true) }
  }

  private class RepositoryRoots(private val myVcs: SvnVcs) {
    private val myRoots = mutableSetOf<Url>()

    fun register(url: Url) {
      myRoots.add(url)
    }

    fun ask(url: Url?, file: VirtualFile): Url? {
      if (url != null) {
        for (root in myRoots) {
          if (isAncestor(root, url)) {
            return root
          }
        }
      }
      // TODO: Seems that RepositoryRoots class should be removed. And necessary repository root should be determined explicitly
      // TODO: using info command.
      val newUrl = SvnUtil.getRepositoryRoot(myVcs, virtualToIoFile(file))
      if (newUrl != null) {
        myRoots.add(newUrl)
        return newUrl
      }
      return null
    }
  }

  class Result {
    val lonelyRoots = mutableListOf<VirtualFile>()
    val topRoots = mutableListOf<RootUrlInfo>()
    val errorRoots = mutableListOf<RootUrlInfo>()
  }
}
