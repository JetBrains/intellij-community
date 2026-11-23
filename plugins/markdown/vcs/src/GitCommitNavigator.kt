// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.vcs

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsProjectLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows the commit of a SHA reference in the main VCS Log.
 *
 * The root of the Markdown file has priority.
 * When only one commit of that root matches the hash, the VCS Log shows this commit.
 * When no commit or more than one commit of that root matches, the VCS Log searches for the hash in all roots.
 * Thus a short hash of the file's repository does not open a commit of another repository.
 * A reference to a commit of another repository still works.
 *
 * With one root, the VCS Log search has no ambiguity, so the navigator does not do its own lookup.
 */
@Service(Service.Level.PROJECT)
internal class GitCommitNavigator(private val project: Project, private val coroutineScope: CoroutineScope) {
  @RequiresEdt
  fun showCommit(root: VirtualFile?, hash: String) {
    val data = VcsProjectLog.getInstance(project).dataManager
    val provider = root?.let { data?.logProviders?.get(it) }
    if (root == null || data == null || data.isDisposed || provider == null || data.logProviders.size <= 1) {
      VcsProjectLog.showRevisionInMainLog(project, createHash(hash))
      return
    }
    val isFullHash = provider.isFullHash(root, hash)

    coroutineScope.launch {
      val commitId = withBackgroundProgress(project, VcsLogBundle.message("vcs.log.show.commit.in.log.process", hash)) {
        withContext(Dispatchers.IO) {
          if (isFullHash) findCommit(data, root, createHash(hash))
          else findSingleCommit(data, root, hash, currentCoroutineContext().job)
        }
      }
      withContext(Dispatchers.EDT) {
        if (commitId != null) {
          VcsProjectLog.showRevisionInMainLog(project, commitId.root, commitId.hash)
        }
        else {
          VcsProjectLog.showRevisionInMainLog(project, createHash(hash))
        }
      }
    }
  }

  /** Returns the commit with the full [hash] in [root]. Returns null when the storage of [root] does not have it. */
  private fun findCommit(data: VcsLogData, root: VirtualFile, hash: Hash): CommitId? {
    val commitId = CommitId(hash, root)
    try {
      return commitId.takeIf { data.storage.containsCommit(it) }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.debug("Cannot find a commit in the VCS Log storage", e)
      return null
    }
  }

  /** Returns the only commit of [root] whose hash starts with [hashPrefix]. Returns null when no commit or more than one commit matches. */
  private fun findSingleCommit(data: VcsLogData, root: VirtualFile, hashPrefix: String, job: Job): CommitId? {
    var result: CommitId? = null
    var isAmbiguous = false
    try {
      data.storage.iterateCommits { commitId ->
        if (commitId.root == root && commitId.hash.asString().startsWith(hashPrefix, ignoreCase = true)) {
          if (result != null) {
            isAmbiguous = true
            return@iterateCommits false
          }
          result = commitId
        }
        job.isActive
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.debug("Cannot find a commit in the VCS Log storage", e)
      return null
    }
    job.ensureActive()
    return if (isAmbiguous) null else result
  }

  private fun createHash(hash: String): Hash = project.service<VcsLogObjectsFactory>().createHash(hash.lowercase())
}

private val LOG = logger<GitCommitNavigator>()
