// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.test

import com.intellij.openapi.vcs.merge.MergeSession
import com.intellij.testFramework.ProjectRule
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.jetbrains.settingsRepository.CannotResolveConflictInTestMode
import org.jetbrains.settingsRepository.SyncType
import org.jetbrains.settingsRepository.conflictResolver
import org.jetbrains.settingsRepository.git.GitRepositoryManager
import org.jetbrains.settingsRepository.git.commit
import org.jetbrains.settingsRepository.git.resetHard
import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.ExternalResource
import java.nio.file.FileSystem
import java.util.*

internal abstract class SettingsRepositoryGitTestBase : IcsTestCase() {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule(runPostStartUpActivities = false)
  }

  protected val repositoryManager: GitRepositoryManager
    get() = icsManager.repositoryManager as GitRepositoryManager

  protected val repository: Repository
    get() = repositoryManager.repository

  protected lateinit var remoteRepository: Repository

  @JvmField
  @Rule
  val remoteRepositoryRule = object: ExternalResource() {
    override fun after() {
      if (::remoteRepository.isInitialized) {
        remoteRepository.close()
      }
    }
  }

  init {
    conflictResolver = { files, mergeProvider ->
      val mergeSession = mergeProvider.createMergeSession(files)
      for (file in files) {
        val mergeData = mergeProvider.loadRevisions(file)
        if (Arrays.equals(mergeData.CURRENT, MARKER_ACCEPT_MY) || Arrays.equals(mergeData.LAST, MARKER_ACCEPT_THEIRS)) {
          mergeSession.conflictResolvedForFile(file, MergeSession.Resolution.AcceptedYours)
        }
        else if (Arrays.equals(mergeData.CURRENT, MARKER_ACCEPT_THEIRS) || Arrays.equals(mergeData.LAST, MARKER_ACCEPT_MY)) {
          mergeSession.conflictResolvedForFile(file, MergeSession.Resolution.AcceptedTheirs)
        }
        else if (Arrays.equals(mergeData.LAST, MARKER_ACCEPT_MY)) {
          file.setBinaryContent(mergeData.LAST)
          mergeProvider.conflictResolvedForFile(file)
        }
        else {
          throw CannotResolveConflictInTestMode()
        }
      }
    }
  }

  class FileInfo(val name: String, val data: ByteArray)

  protected suspend fun addAndCommit(path: String): FileInfo {
    val data = """<file path="$path" />""".toByteArray()
    provider.write(path, data)
    repositoryManager.commit()
    return FileInfo(path, data)
  }

  protected fun createRemoteRepository(branchName: String? = null, initialCommit: Boolean = false) {
    val repository = tempDirManager.createRepository("upstream")
    if (initialCommit) {
      repository
        .add(SAMPLE_FILE_NAME, SAMPLE_FILE_CONTENT)
        .commit("")
    }
    if (branchName != null) {
      if (!initialCommit) {
        // jgit cannot checkout&create branch if no HEAD (no commits in our empty repository), so we create initial empty commit
        repository.commit("")
      }
      Git(repository).checkout().setCreateBranch(true).setName(branchName).call()
    }

    remoteRepository = repository
  }

  protected fun restoreRemoteAfterPush() {
    /** we must not push to non-bare repository - but we do it in test (our sync merge equals to "pull&push"),
    "
    By default, updating the current branch in a non-bare repository
    is denied, because it will make the index and work tree inconsistent
    with what you pushed, and will require 'git reset --hard' to match the work tree to HEAD.
    "
    so, we do "git reset --hard"
     */
    remoteRepository.resetHard()
  }

  protected suspend fun sync(syncType: SyncType) {
    icsManager.sync(syncType)
  }

  protected suspend fun createLocalAndRemoteRepositories(remoteBranchName: String? = null, initialCommit: Boolean = false) {
    createRemoteRepository(remoteBranchName, true)
    configureLocalRepository(remoteBranchName)
    if (initialCommit) {
      addAndCommit("local.xml")
    }
  }

  protected fun configureLocalRepository(remoteBranchName: String? = null) {
    repositoryManager.setUpstream(remoteRepository.workTree.absolutePath, remoteBranchName)
  }

  protected fun FileSystem.compare(): FileSystem {
    val root = getPath("/")
    compareFiles(root, repository.workTreePath)
    compareFiles(root, remoteRepository.workTreePath)
    return this
  }
}