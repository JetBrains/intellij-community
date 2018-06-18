// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.BackgroundTaskQueue
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.ZipperUpdater
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.idea.svn.SvnUtil.isAncestor
import org.jetbrains.idea.svn.api.Url

// 1. listen to roots changes
// 2. - possibly - to deletion/checkouts??? what if WC roots can be
class RootsToWorkingCopies(private val myVcs: SvnVcs) : VcsListener {
  private val myLock = Any()
  private val myProject = myVcs.project
  private val myRootMapping = mutableMapOf<VirtualFile, WorkingCopy>()
  private val myUnversioned = mutableSetOf<VirtualFile>()
  private val myQueue = BackgroundTaskQueue(myProject, "SVN VCS roots authorization checker")
  private val myZipperUpdater = ZipperUpdater(200, Alarm.ThreadToUse.POOLED_THREAD, myProject)
  private val myRechecker = Runnable {
    val roots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(myVcs)
    synchronized(myLock) {
      clear()
      for (root in roots) {
        addRoot(root)
      }
    }
  }

  private fun addRoot(root: VirtualFile) {
    myQueue.run(object : Task.Backgroundable(myProject, "Looking for '${root.path}' working copy root", false) {
      override fun run(indicator: ProgressIndicator) {
        calculateRoot(root)
      }
    })
  }

  @CalledInBackground
  fun getMatchingCopy(url: Url?): WorkingCopy? {
    assert(!ApplicationManager.getApplication().isDispatchThread || ApplicationManager.getApplication().isUnitTestMode)
    if (url == null) return null

    val roots = ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(SvnVcs.getInstance(myProject))
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

  @CalledInBackground
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
      val svnInfo = myVcs.getInfo(workingCopyRoot)

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

  fun clear() = synchronized(myLock) {
    myRootMapping.clear()
    myUnversioned.clear()
    myZipperUpdater.stop()
  }

  override fun directoryMappingChanged() {
    // todo +- here... shouldnt be
    myVcs.authNotifier.clear()

    myZipperUpdater.queue(myRechecker)
  }
}
