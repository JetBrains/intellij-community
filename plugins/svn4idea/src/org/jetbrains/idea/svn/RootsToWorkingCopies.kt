// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.BackgroundTaskQueue
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ZipperUpdater
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.idea.svn.SvnBundle.message
import org.jetbrains.idea.svn.SvnUtil.isAncestor
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.auth.SvnAuthenticationNotifier

// 1. listen to roots changes
// 2. - possibly - to deletion/checkouts??? what if WC roots can be
@Service
class RootsToWorkingCopies(private val project: Project) : VcsListener, Disposable {
  private val myLock = Any()
  private val myRootMapping = mutableMapOf<VirtualFile, WorkingCopy>()
  private val myUnversioned = mutableSetOf<VirtualFile>()
  private val myQueue = BackgroundTaskQueue(project, message("progress.title.svn.roots.authorization.checker"))
  private val myZipperUpdater = ZipperUpdater(200, this)
  private val myRechecker = Runnable {
    clear()
    val roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs)
    for (root in roots) {
      addRoot(root)
    }
  }

  private val vcs: SvnVcs get() = SvnVcs.getInstance(project)

  init {
    project.messageBus.connect().subscribe(VCS_CONFIGURATION_CHANGED, this)
  }

  private fun addRoot(root: VirtualFile) {
    myQueue.run(object : Task.Backgroundable(project, message("progress.title.looking.for.file.working.copy.root", root.path), false) {
      override fun run(indicator: ProgressIndicator) {
        calculateRoot(root)
      }
    })
  }

  @RequiresBackgroundThread
  fun getMatchingCopy(url: Url?): WorkingCopy? {
    assert(!ApplicationManager.getApplication().isDispatchThread || ApplicationManager.getApplication().isUnitTestMode)
    if (url == null) return null

    val roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs)
    synchronized(myLock) {
      for (root in roots) {
        val wcRoot = getWcRoot(root)
        if (wcRoot != null && (isAncestor(wcRoot.url, url) || isAncestor(url, wcRoot.url))) {
          return wcRoot
        }
      }
    }
    return null
  }

  @RequiresBackgroundThread
  fun getWcRoot(root: VirtualFile): WorkingCopy? {
    assert(!ApplicationManager.getApplication().isDispatchThread || ApplicationManager.getApplication().isUnitTestMode)

    synchronized(myLock) {
      if (myUnversioned.contains(root)) return null
      val existing = myRootMapping[root]
      if (existing != null) return existing
    }
    return calculateRoot(root)
  }

  private fun calculateRoot(root: VirtualFile): WorkingCopy? {
    val workingCopyRoot = SvnUtil.getWorkingCopyRoot(virtualToIoFile(root))
    var workingCopy: WorkingCopy? = null

    if (workingCopyRoot != null) {
      val svnInfo = vcs.getInfo(workingCopyRoot)

      if (svnInfo != null && svnInfo.url != null) {
        workingCopy = WorkingCopy(workingCopyRoot, svnInfo.url)
      }
    }

    return registerWorkingCopy(root, workingCopy)
  }

  private fun registerWorkingCopy(root: VirtualFile, resolvedWorkingCopy: WorkingCopy?): WorkingCopy? {
    synchronized(myLock) {
      if (resolvedWorkingCopy == null) {
        myRootMapping.remove(root)
        myUnversioned.add(root)
      }
      else {
        myUnversioned.remove(root)
        myRootMapping[root] = resolvedWorkingCopy
      }
    }
    return resolvedWorkingCopy
  }

  override fun dispose() = clearData()

  fun clear() {
    clearData()
    myZipperUpdater.stop()
  }

  private fun clearData() =
    synchronized(myLock) {
      myRootMapping.clear()
      myUnversioned.clear()
    }

  override fun directoryMappingChanged() {
    // todo +- here... shouldnt be
    SvnAuthenticationNotifier.getInstance(project).clear()

    myZipperUpdater.queue(myRechecker)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): RootsToWorkingCopies = project.service()
  }
}
