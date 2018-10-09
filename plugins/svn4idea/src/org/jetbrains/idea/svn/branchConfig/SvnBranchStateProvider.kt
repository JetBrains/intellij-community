// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VfsUtilCore.getRelativePath
import com.intellij.util.text.nullize
import com.intellij.vcs.branch.BranchData
import com.intellij.vcs.branch.BranchDataImpl
import com.intellij.vcs.branch.BranchStateProvider
import org.jetbrains.idea.svn.SvnVcs

private fun join(s1: String, s2: String?) = listOfNotNull(s1, s2.nullize()).joinToString("/")

class SvnBranchStateProvider(val project: Project, val vcsManager: ProjectLevelVcsManager) : BranchStateProvider {
  // do not inject in constructor as SvnBranchConfigurationManager.getInstance() has custom logic
  private val branchManager = SvnBranchConfigurationManager.getInstance(project)

  override fun getCurrentBranch(path: FilePath): BranchData? {
    if (!vcsManager.checkVcsIsActive(SvnVcs.VCS_NAME)) return null

    val wcRoot = (vcsManager.getVcsFor(path) as? SvnVcs)?.svnFileUrlMapping?.getWcRootForFilePath(path.ioFile) ?: return null
    val configuration = branchManager.svnBranchConfigManager.getConfigOrNull(wcRoot.virtualFile) ?: return null
    val branchUrl = configuration.getWorkingBranch(wcRoot.url) ?: return null
    val presentableRootName = join(wcRoot.root.presentableName, getRelativePath(wcRoot.virtualFile, wcRoot.root))

    // TODO it could make sense to add this check directly to SvnBranchConfigurationNew.getBaseName()
    return BranchDataImpl(presentableRootName, if (branchUrl == configuration.trunk) "trunk" else branchUrl.tail)
  }
}