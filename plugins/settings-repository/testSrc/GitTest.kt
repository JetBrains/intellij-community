// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.test

import com.intellij.configurationStore.ApplicationStoreImpl
import com.intellij.configurationStore.write
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vcs.merge.MergeSession
import com.intellij.testFramework.file
import com.intellij.util.PathUtilRt
import com.intellij.util.io.delete
import com.intellij.util.io.writeChild
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.settingsRepository.CannotResolveConflictInTestMode
import org.jetbrains.settingsRepository.SyncType
import org.jetbrains.settingsRepository.conflictResolver
import org.jetbrains.settingsRepository.copyLocalConfig
import org.jetbrains.settingsRepository.git.commit
import org.jetbrains.settingsRepository.git.computeIndexDiff
import org.jetbrains.settingsRepository.git.deletePath
import org.jetbrains.settingsRepository.git.writePath
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.*

val MARKER_ACCEPT_MY = "__accept my__".toByteArray()
val MARKER_ACCEPT_THEIRS = "__accept theirs__".toByteArray()

internal class GitTest : GitTestCase() {
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

  @Test fun add() {
    provider.write(SAMPLE_FILE_NAME, SAMPLE_FILE_CONTENT)

    val diff = repository.computeIndexDiff()
    assertThat(diff.diff()).isTrue()
    assertThat(diff.added).containsOnly(SAMPLE_FILE_NAME)
    assertThat(diff.changed).isEmpty()
    assertThat(diff.removed).isEmpty()
    assertThat(diff.modified).isEmpty()
    assertThat(diff.untracked).isEmpty()
    assertThat(diff.untrackedFolders).isEmpty()
  }

  @Test fun addSeveral() {
    val addedFile = "foo.xml"
    val addedFile2 = "bar.xml"
    provider.write(addedFile, "foo")
    provider.write(addedFile2, "bar")

    val diff = repository.computeIndexDiff()
    assertThat(diff.diff()).isTrue()
    assertThat(diff.added).containsOnly(addedFile, addedFile2)
    assertThat(diff.changed).isEmpty()
    assertThat(diff.removed).isEmpty()
    assertThat(diff.modified).isEmpty()
    assertThat(diff.untracked).isEmpty()
    assertThat(diff.untrackedFolders).isEmpty()
  }

  @Test fun delete() {
    fun delete(directory: Boolean) {
      val dir = "dir"
      val fullFileSpec = "$dir/file.xml"
      provider.write(fullFileSpec, SAMPLE_FILE_CONTENT)
      provider.delete(if (directory) dir else fullFileSpec)

      val diff = repository.computeIndexDiff()
      assertThat(diff.diff()).isFalse()
      assertThat(diff.added).isEmpty()
      assertThat(diff.changed).isEmpty()
      assertThat(diff.removed).isEmpty()
      assertThat(diff.modified).isEmpty()
      assertThat(diff.untracked).isEmpty()
      assertThat(diff.untrackedFolders).isEmpty()
    }

    delete(false)
    delete(true)
  }

  @Test fun `set upstream`() {
    val url = "https://github.com/user/repo.git"
    repositoryManager.setUpstream(url)
    assertThat(repositoryManager.getUpstream()).isEqualTo(url)
  }

  @Test
  fun pullToRepositoryWithoutCommits() = runBlocking {
    doPullToRepositoryWithoutCommits(null)
  }

  @Test
  fun pullToRepositoryWithoutCommitsAndCustomRemoteBranchName() = runBlocking {
    doPullToRepositoryWithoutCommits("customRemoteBranchName")
  }

  private suspend fun doPullToRepositoryWithoutCommits(remoteBranchName: String?) {
    createLocalAndRemoteRepositories(remoteBranchName)
    repositoryManager.pull()
    compareFiles(repository.workTreePath, remoteRepository.workTreePath)
  }

  @Test
  fun pullToRepositoryWithCommits() = runBlocking {
    doPullToRepositoryWithCommits(null)
  }

  @Test
  fun pullToRepositoryWithCommitsAndCustomRemoteBranchName() = runBlocking {
    doPullToRepositoryWithCommits("customRemoteBranchName")
  }

  private suspend fun doPullToRepositoryWithCommits(remoteBranchName: String?) {
    createLocalAndRemoteRepositories(remoteBranchName)
    val file = addAndCommit("local.xml")

    repositoryManager.commit()
    repositoryManager.pull()
    assertThat(repository.workTree.resolve(file.name)).hasBinaryContent(file.data)
    compareFiles(repository.workTreePath, remoteRepository.workTreePath, PathUtilRt.getFileName(file.name))
  }

  // never was merged. we reset using "merge with strategy "theirs", so, we must test - what's happen if it is not first merge? - see next test
  @Test
  fun resetToTheirsIfFirstMerge() = runBlocking<Unit> {
    createLocalAndRemoteRepositories(initialCommit = true)

    sync(SyncType.OVERWRITE_LOCAL)
    fs
      .file(SAMPLE_FILE_NAME, SAMPLE_FILE_CONTENT)
      .compare()
  }

  @Test fun `overwrite local - second merge is null`() = runBlocking {
    createLocalAndRemoteRepositories(initialCommit = true)

    sync(SyncType.MERGE)

    restoreRemoteAfterPush()

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

  @Test fun `merge - resolve conflicts to my`() = runBlocking<Unit> {
    createLocalAndRemoteRepositories()

    val data = MARKER_ACCEPT_MY
    provider.write(SAMPLE_FILE_NAME, data)

    sync(SyncType.MERGE)

    restoreRemoteAfterPush()
    fs.file(SAMPLE_FILE_NAME, data.toString(StandardCharsets.UTF_8)).compare()
  }

  @Test fun `merge - theirs file deleted, my modified, accept theirs`() = runBlocking<Unit> {
    createLocalAndRemoteRepositories()

    sync(SyncType.MERGE)

    val data = MARKER_ACCEPT_THEIRS
    provider.write(SAMPLE_FILE_NAME, data)
    repositoryManager.commit()

    remoteRepository.deletePath(SAMPLE_FILE_NAME)
    remoteRepository.commit("delete $SAMPLE_FILE_NAME")

    sync(SyncType.MERGE)

    fs.compare()
  }

  @Test
  fun `merge - my file deleted, theirs modified, accept my`() = runBlocking<Unit> {
    createLocalAndRemoteRepositories()

    sync(SyncType.MERGE)

    provider.delete(SAMPLE_FILE_NAME)
    repositoryManager.commit()

    remoteRepository.writePath(SAMPLE_FILE_NAME, MARKER_ACCEPT_THEIRS)
    remoteRepository.commit("")

    sync(SyncType.MERGE)
    restoreRemoteAfterPush()

    fs.compare()
  }

  @Test
  fun `commit if unmerged`() = runBlocking<Unit> {
    createLocalAndRemoteRepositories()

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

    fs.file(SAMPLE_FILE_NAME, SAMPLE_FILE_CONTENT).compare()
  }

  // remote is uninitialized (empty - initial commit is not done)
  @Test fun `merge with uninitialized upstream`() {
    doSyncWithUninitializedUpstream(SyncType.MERGE)
  }

  @Test fun `overwrite remote - uninitialized upstream`() {
    doSyncWithUninitializedUpstream(SyncType.OVERWRITE_REMOTE)
  }

  @Test fun `overwrite local - uninitialized upstream`() {
    doSyncWithUninitializedUpstream(SyncType.OVERWRITE_LOCAL)
  }

  @Test
  fun `remove deleted files`() = runBlocking<Unit> {
    createLocalAndRemoteRepositories()

    val workDir = repositoryManager.repository.workTree.toPath()
    provider.write("foo.xml", SAMPLE_FILE_CONTENT)
    sync(SyncType.MERGE)

    var diff = repository.computeIndexDiff()
    assertThat(diff.diff()).isFalse()

    val file = workDir.resolve("foo.xml")
    assertThat(file).isRegularFile()
    file.delete()

    diff = repository.computeIndexDiff()
    assertThat(diff.diff()).isTrue()
    assertThat(diff.added).isEmpty()
    assertThat(diff.changed).isEmpty()
    assertThat(diff.removed).isEmpty()
    assertThat(diff.modified).isEmpty()
    assertThat(diff.untracked).isEmpty()
    assertThat(diff.untrackedFolders).isEmpty()
    assertThat(diff.missing).containsOnly("foo.xml")

    sync(SyncType.MERGE)

    diff = repository.computeIndexDiff()
    assertThat(diff.diff()).isFalse()
  }

  @Test
  fun gitignore() = runBlocking {
    createLocalAndRemoteRepositories()

    provider.write(".gitignore", "*.html")
    sync(SyncType.MERGE)

    val workDir = repositoryManager.repository.workTree.toPath()

    val filePaths = listOf("bar.html", "i/am/a/long/path/to/file/foo.html")
    for (path in filePaths) {
      provider.write(path, path)
    }

    fun assertThatFileExist() {
      for (path in filePaths) {
        assertThat(workDir.resolve(path)).isRegularFile()
      }
    }

    assertThatFileExist()

    fun assertStatus() {
      val diff = repository.computeIndexDiff()
      assertThat(diff.diff()).isFalse()
      assertThat(diff.added).isEmpty()
      assertThat(diff.changed).isEmpty()
      assertThat(diff.removed).isEmpty()
      assertThat(diff.modified).isEmpty()
      assertThat(diff.untracked).isEmpty()
      assertThat(diff.untrackedFolders).containsOnly("i")
    }

    assertStatus()

    for (path in filePaths) {
      provider.read(path) {
        assertThat(it).isNotNull()
      }
    }

    assertThatFileExist()

    sync(SyncType.MERGE)

    assertThatFileExist()
    assertStatus()
  }

  @Test
  fun `initial copy to repository - no local files`() = runBlocking {
    createRemoteRepository(initialCommit = false)
    // check error during findRemoteRefUpdatesFor (no master ref)
    testInitialCopy(false)
  }

  @Test
  fun `initial copy to repository - some local files`() = runBlocking {
    createRemoteRepository(initialCommit = false)
    // check error during findRemoteRefUpdatesFor (no master ref)
    testInitialCopy(true)
  }

  @Test
  fun `initial copy to repository - remote files removed`() = runBlocking {
    createRemoteRepository(initialCommit = true)

    // check error during findRemoteRefUpdatesFor (no master ref)
    testInitialCopy(true, SyncType.OVERWRITE_REMOTE)
  }

  private suspend fun testInitialCopy(addLocalFiles: Boolean, syncType: SyncType = SyncType.MERGE) {
    repositoryManager.createRepositoryIfNeed()
    repositoryManager.setUpstream(remoteRepository.workTree.absolutePath)

    val store = ApplicationStoreImpl(ApplicationManager.getApplication()!!)
    val localConfigPath = tempDirManager.newPath("local_config", refreshVfs = true)

    val lafData = """<application>
      <component name="UISettings">
        <option name="HIDE_TOOL_STRIPES" value="false" />
      </component>
    </application>"""
    if (addLocalFiles) {
      localConfigPath.writeChild("options/ui.lnf.xml", lafData)
    }

    store.setPath(localConfigPath.toString())
    store.storageManager.addStreamProvider(provider)

    icsManager.sync(syncType, GitTestCase.projectRule.project) { copyLocalConfig(store.storageManager) }

    if (addLocalFiles) {
      assertThat(localConfigPath).isDirectory()
      fs
        .file("ui.lnf.xml", lafData)
      restoreRemoteAfterPush()
    }
    else {
      assertThat(localConfigPath).doesNotExist()
    }
    fs.compare()
  }

  private fun doSyncWithUninitializedUpstream(syncType: SyncType) = runBlocking<Unit> {
    createRemoteRepository(initialCommit = false)
    repositoryManager.setUpstream(remoteRepository.workTree.absolutePath)

    val path = "local.xml"
    val data = "<application />"
    provider.write(path, data)

    sync(syncType)

    if (syncType != SyncType.OVERWRITE_LOCAL) {
      fs.file(path, data)
    }
    restoreRemoteAfterPush()
    fs.compare()
  }
}