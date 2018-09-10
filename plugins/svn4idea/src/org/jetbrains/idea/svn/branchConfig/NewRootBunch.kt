// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.ProgressManagerQueue
import org.jetbrains.idea.svn.SvnVcs
import org.jetbrains.idea.svn.api.Url

private val LOG = logger<NewRootBunch>()

// synch is here
class NewRootBunch(private val myProject: Project, private val myBranchesLoader: ProgressManagerQueue) {
  private val myLock = Any()
  private val myMap = mutableMapOf<VirtualFile, InfoStorage<SvnBranchConfigurationNew>>()

  val mapCopy: Map<VirtualFile, SvnBranchConfigurationNew> get() = synchronized(myLock) { myMap.mapValues { it.value.value } }

  fun updateForRoot(root: VirtualFile, config: InfoStorage<SvnBranchConfigurationNew>, reload: Boolean): Unit = synchronized(myLock) {
    val previous: SvnBranchConfigurationNew?
    val override: Boolean
    val existing = myMap[root]

    if (existing == null) {
      previous = null
      override = true
      myMap[root] = config
    }
    else {
      previous = existing.value
      override = existing.accept(config)
    }

    if (reload && override) {
      myBranchesLoader.run { reloadBranches(root, previous, config.value) }
    }
  }

  fun updateBranches(root: VirtualFile, branchesParent: Url, items: InfoStorage<List<SvnBranchItem>>): Unit = synchronized(myLock) {
    val existing = myMap[root]
    if (existing == null) {
      LOG.info("cannot update branches, branches parent not found: ${branchesParent.toDecodedString()}")
    }
    else {
      existing.value.updateBranch(branchesParent, items)
    }
  }

  fun getConfig(root: VirtualFile): SvnBranchConfigurationNew = synchronized(myLock) {
    val value = myMap[root]
    val result: SvnBranchConfigurationNew
    if (value == null) {
      result = SvnBranchConfigurationNew()
      myMap[root] = InfoStorage(result, InfoReliability.empty)
      myBranchesLoader.run(DefaultBranchConfigInitializer(myProject, this, root))
    }
    else {
      result = value.value
    }
    result
  }

  fun reloadBranchesAsync(root: VirtualFile, branchLocation: Url, reliability: InfoReliability) {
    getApplication().executeOnPooledThread { reloadBranches(root, branchLocation, reliability, true) }
  }

  fun reloadBranches(root: VirtualFile, prev: SvnBranchConfigurationNew?, next: SvnBranchConfigurationNew) {
    val oldUrls = prev?.branchLocations?.toSet() ?: emptySet()
    val vcs = SvnVcs.getInstance(myProject)
    if (!vcs.isVcsBackgroundOperationsAllowed(root)) return

    for (newBranchUrl in next.branchLocations) {
      // check if cancel had been put
      if (!vcs.isVcsBackgroundOperationsAllowed(root)) return
      if (newBranchUrl !in oldUrls) {
        reloadBranches(root, newBranchUrl, InfoReliability.defaultValues, true)
      }
    }
  }

  fun reloadBranches(root: VirtualFile, branchLocation: Url, reliability: InfoReliability, passive: Boolean): Unit =
    BranchesLoader(myProject, this, branchLocation, reliability, root, passive).run()
}