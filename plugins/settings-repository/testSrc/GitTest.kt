/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository.test

import com.intellij.configurationStore.write
import com.intellij.mock.MockVirtualFileSystem
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.merge.MergeSession
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.path
import com.intellij.util.PathUtilRt
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.jetbrains.jgit.dirCache.deletePath
import org.jetbrains.jgit.dirCache.writePath
import org.jetbrains.settingsRepository.CannotResolveConflictInTestMode
import org.jetbrains.settingsRepository.SyncType
import org.jetbrains.settingsRepository.conflictResolver
import org.jetbrains.settingsRepository.git.GitRepositoryManager
import org.jetbrains.settingsRepository.git.commit
import org.jetbrains.settingsRepository.git.computeIndexDiff
import org.jetbrains.settingsRepository.git.resetHard
import org.junit.ClassRule
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Arrays
import kotlin.properties.Delegates

// kotlin bug, cannot be val (.NoSuchMethodError: org.jetbrains.settingsRepository.SettingsRepositoryPackage.getMARKER_ACCEPT_MY()[B)
object AM {
  val MARKER_ACCEPT_MY: ByteArray = "__accept my__".toByteArray()
  val MARKER_ACCEPT_THEIRS: ByteArray = "__accept theirs__".toByteArray()
}

class GitTest : IcsTestCase() {
  companion object {
    @ClassRule val projectRule = ProjectRule()
  }

  private val repositoryManager: GitRepositoryManager
    get() = icsManager.repositoryManager as GitRepositoryManager

  private val repository: Repository
    get() = repositoryManager.repository

  var remoteRepository: Repository by Delegates.notNull()

  init {
    conflictResolver = { files, mergeProvider ->
      val mergeSession = mergeProvider.createMergeSession(files)
      for (file in files) {
        val mergeData = mergeProvider.loadRevisions(file)
        if (Arrays.equals(mergeData.CURRENT, AM.MARKER_ACCEPT_MY) || Arrays.equals(mergeData.LAST, AM.MARKER_ACCEPT_THEIRS)) {
          mergeSession.conflictResolvedForFile(file, MergeSession.Resolution.AcceptedYours)
        }
        else if (Arrays.equals(mergeData.CURRENT, AM.MARKER_ACCEPT_THEIRS) || Arrays.equals(mergeData.LAST, AM.MARKER_ACCEPT_MY)) {
          mergeSession.conflictResolvedForFile(file, MergeSession.Resolution.AcceptedTheirs)
        }
        else if (Arrays.equals(mergeData.LAST, AM.MARKER_ACCEPT_MY)) {
          file.setBinaryContent(mergeData.LAST!!)
          mergeProvider.conflictResolvedForFile(file)
        }
        else {
          throw CannotResolveConflictInTestMode()
        }
      }
    }
  }

  private fun addAndCommit(path: String): FileInfo {
    val data = """<file path="$path" />""".toByteArray()
    provider.write(path, data)
    repositoryManager.commit()
    return FileInfo(path, data)
  }

  Test fun add() {
    provider.write(SAMPLE_FILE_NAME, SAMPLE_FILE_CONTENT)

    val diff = repository.computeIndexDiff()
    assertThat(diff.diff()).isTrue()
    assertThat(diff.getAdded()).containsOnly(SAMPLE_FILE_NAME)
    assertThat(diff.getChanged()).isEmpty()
    assertThat(diff.getRemoved()).isEmpty()
    assertThat(diff.getModified()).isEmpty()
    assertThat(diff.getUntracked()).isEmpty()
    assertThat(diff.getUntrackedFolders()).isEmpty()
  }

  Test fun addSeveral() {
    val addedFile = "foo.xml"
    val addedFile2 = "bar.xml"
    provider.write(addedFile, "foo")
    provider.write(addedFile2, "bar")

    val diff = repository.computeIndexDiff()
    assertThat(diff.diff()).isTrue()
    assertThat(diff.getAdded()).containsOnly(addedFile, addedFile2)
    assertThat(diff.getChanged()).isEmpty()
    assertThat(diff.getRemoved()).isEmpty()
    assertThat(diff.getModified()).isEmpty()
    assertThat(diff.getUntracked()).isEmpty()
    assertThat(diff.getUntrackedFolders()).isEmpty()
  }

  Test fun delete() {
    fun delete(directory: Boolean) {
      val dir = "dir"
      val fullFileSpec = "$dir/file.xml"
      provider.write(fullFileSpec, SAMPLE_FILE_CONTENT)
      provider.delete(if (directory) dir else fullFileSpec)

      val diff = repository.computeIndexDiff()
      assertThat(diff.diff()).isFalse()
      assertThat(diff.getAdded()).isEmpty()
      assertThat(diff.getChanged()).isEmpty()
      assertThat(diff.getRemoved()).isEmpty()
      assertThat(diff.getModified()).isEmpty()
      assertThat(diff.getUntracked()).isEmpty()
      assertThat(diff.getUntrackedFolders()).isEmpty()
    }

    delete(false)
    delete(true)
  }

  Test fun `set upstream`() {
    val url = "https://github.com/user/repo.git"
    repositoryManager.setUpstream(url)
    assertThat(repositoryManager.getUpstream()).isEqualTo(url)
  }

  Test
  public fun pullToRepositoryWithoutCommits() {
    doPullToRepositoryWithoutCommits(null)
  }

  Test fun pullToRepositoryWithoutCommitsAndCustomRemoteBranchName() {
    doPullToRepositoryWithoutCommits("customRemoteBranchName")
  }

  private fun doPullToRepositoryWithoutCommits(remoteBranchName: String?) {
    createLocalRepository(remoteBranchName)
    repositoryManager.pull()
    compareFiles(repository.workTree, remoteRepository.workTree)
  }

  Test fun pullToRepositoryWithCommits() {
    doPullToRepositoryWithCommits(null)
  }

  Test fun pullToRepositoryWithCommitsAndCustomRemoteBranchName() {
    doPullToRepositoryWithCommits("customRemoteBranchName")
  }

  private fun doPullToRepositoryWithCommits(remoteBranchName: String?) {
    val file = createLocalRepositoryAndCommit(remoteBranchName)

    repositoryManager.commit()
    repositoryManager.pull()
    assertThat(FileUtil.loadFile(File(repository.getWorkTree(), file.name))).isEqualTo(String(file.data, CharsetToolkit.UTF8_CHARSET))
    compareFiles(repository.workTree, remoteRepository.workTree, null, PathUtilRt.getFileName(file.name))
  }

  private fun createLocalRepository(remoteBranchName: String? = null) {
    createRemoteRepository(remoteBranchName)
    repositoryManager.setUpstream(remoteRepository.getWorkTree().getAbsolutePath(), remoteBranchName)
  }

  private fun createLocalRepositoryAndCommit(remoteBranchName: String? = null): FileInfo {
    createLocalRepository(remoteBranchName)
    return addAndCommit("local.xml")
  }

  private fun MockVirtualFileSystem.compare() {
    compareFiles(repository.workTree, remoteRepository.workTree, getRoot())
  }

  // never was merged. we reset using "merge with strategy "theirs", so, we must test - what's happen if it is not first merge? - see next test
  Test fun resetToTheirsIfFirstMerge() {
    createLocalRepositoryAndCommit(null)
    sync(SyncType.OVERWRITE_LOCAL)
    fs().file(SAMPLE_FILE_NAME, SAMPLE_FILE_CONTENT).compare()
  }

  Test fun resetToTheirsISecondMergeIsNull() {
    createLocalRepositoryAndCommit(null)
    sync(SyncType.MERGE)

    restoreRemoteAfterPush()

    val fs = MockVirtualFileSystem()

    fun testRemote() {
      fs
        .file("local.xml", """<file path="local.xml" />""")
        .file(SAMPLE_FILE_NAME, SAMPLE_FILE_CONTENT)
        .compare()
    }
    testRemote()

    addAndCommit("_mac/local2.xml")
    sync(SyncType.OVERWRITE_LOCAL)

    fs.compare()

    // test: merge and push to remote after such reset
    sync(SyncType.MERGE)

    restoreRemoteAfterPush()

    testRemote()
  }

  Test fun resetToMyIfFirstMerge() {
    createLocalRepositoryAndCommit()
    sync(SyncType.OVERWRITE_REMOTE)
    restoreRemoteAfterPush()
    fs().file("local.xml", """<file path="local.xml" />""").compare()
  }

  Test fun `reset to my, second merge is null`() {
    createLocalRepositoryAndCommit()
    sync(SyncType.MERGE)

    restoreRemoteAfterPush()

    val fs = fs().file("local.xml", """<file path="local.xml" />""").file(SAMPLE_FILE_NAME, SAMPLE_FILE_CONTENT)
    fs.compare()

    val localToFilePath = "_mac/local2.xml"
    addAndCommit(localToFilePath)
    sync(SyncType.OVERWRITE_REMOTE)
    restoreRemoteAfterPush()

    fs.file(localToFilePath, """<file path="$localToFilePath" />""")
    fs.compare()

    // test: merge to remote after such reset
    sync(SyncType.MERGE)

    restoreRemoteAfterPush()

    fs.compare()
  }

  Test fun `merge - resolve conflicts to my`() {
    createLocalRepository()

    val data = AM.MARKER_ACCEPT_MY
    provider.write(SAMPLE_FILE_NAME, data)

    sync(SyncType.MERGE)

    restoreRemoteAfterPush()
    fs().file(SAMPLE_FILE_NAME, data.toString(StandardCharsets.UTF_8)).compare()
  }

  Test fun `merge - theirs file deleted, my modified, accept theirs`() {
    createLocalRepository()

    sync(SyncType.MERGE)

    val data = AM.MARKER_ACCEPT_THEIRS
    provider.write(SAMPLE_FILE_NAME, data)
    repositoryManager.commit()

    remoteRepository.deletePath(SAMPLE_FILE_NAME)
    remoteRepository.commit("delete $SAMPLE_FILE_NAME")

    sync(SyncType.MERGE)

    fs().compare()
  }

  Test fun `merge - my file deleted, theirs modified, accept my`() {
    createLocalRepository()

    sync(SyncType.MERGE)

    provider.delete("remote.xml")
    repositoryManager.commit()

    remoteRepository.writePath("remote.xml", AM.MARKER_ACCEPT_THEIRS)
    remoteRepository.commit("")

    sync(SyncType.MERGE)
    restoreRemoteAfterPush()

    fs().compare()
  }

  Test fun `commit if unmerged`() {
    createLocalRepository()

    val data = "<foo />"
    provider.write(SAMPLE_FILE_NAME, data)

    try {
      sync(SyncType.MERGE)
    }
    catch (e: CannotResolveConflictInTestMode) {
    }

    // repository in unmerged state
    conflictResolver = {files, mergeProvider ->
      assertThat(files).hasSize(1)
      assertThat(files.first().path).isEqualTo(SAMPLE_FILE_NAME)
      val mergeSession = mergeProvider.createMergeSession(files)
      mergeSession.conflictResolvedForFile(files.first(), MergeSession.Resolution.AcceptedTheirs)
    }
    sync(SyncType.MERGE)

    fs().file(SAMPLE_FILE_NAME, SAMPLE_FILE_CONTENT).compare()
  }

  // remote is uninitialized (empty - initial commit is not done)
  Test fun `merge with uninitialized upstream`() {
    doSyncWithUninitializedUpstream(SyncType.MERGE)
  }

  Test fun `reset to my, uninitialized upstream`() {
    doSyncWithUninitializedUpstream(SyncType.OVERWRITE_REMOTE)
  }

  Test fun `reset to theirs, uninitialized upstream`() {
    doSyncWithUninitializedUpstream(SyncType.OVERWRITE_LOCAL)
  }

  Test fun gitignore() {
    createLocalRepository()

    provider.write(".gitignore", "*.html")
    sync(SyncType.MERGE)

    val filePaths = listOf("bar.html", "i/am/a/long/path/to/file/foo.html")
    for (path in filePaths) {
      provider.write(path, path)
    }

    val diff = repository.computeIndexDiff()
    assertThat(diff.diff()).isFalse()
    assertThat(diff.getAdded()).isEmpty()
    assertThat(diff.getChanged()).isEmpty()
    assertThat(diff.getRemoved()).isEmpty()
    assertThat(diff.getModified()).isEmpty()
    assertThat(diff.getUntracked()).isEmpty()
    assertThat(diff.getUntrackedFolders()).isEmpty()

    for (path in filePaths) {
      assertThat(provider.read(path)).isNull()
    }
  }

  private fun createRemoteRepository(branchName: String? = null, initialCommit: Boolean = true) {
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

  private fun doSyncWithUninitializedUpstream(syncType: SyncType) {
    createRemoteRepository(initialCommit = false)
    repositoryManager.setUpstream(remoteRepository.getWorkTree().getAbsolutePath())

    val path = "local.xml"
    val data = "<application />"
    provider.write(path, data)

    sync(syncType)

    val fs = MockVirtualFileSystem()
    if (syncType != SyncType.OVERWRITE_LOCAL) {
      fs.file(path, data)
    }
    restoreRemoteAfterPush();
    fs.compare()
  }

  private fun restoreRemoteAfterPush() {
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

  private fun sync(syncType: SyncType) {
    icsManager.sync(syncType)
  }
}