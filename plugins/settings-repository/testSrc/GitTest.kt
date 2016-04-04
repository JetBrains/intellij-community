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

import com.intellij.configurationStore.ApplicationStoreImpl
import com.intellij.configurationStore.write
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vcs.merge.MergeSession
import com.intellij.testFramework.file
import com.intellij.util.PathUtilRt
import com.intellij.util.writeChild
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jgit.dirCache.deletePath
import org.jetbrains.jgit.dirCache.writePath
import org.jetbrains.settingsRepository.CannotResolveConflictInTestMode
import org.jetbrains.settingsRepository.SyncType
import org.jetbrains.settingsRepository.conflictResolver
import org.jetbrains.settingsRepository.copyLocalConfig
import org.jetbrains.settingsRepository.git.commit
import org.jetbrains.settingsRepository.git.computeIndexDiff
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.*

// kotlin bug, cannot be val (.NoSuchMethodError: org.jetbrains.settingsRepository.SettingsRepositoryPackage.getMARKER_ACCEPT_MY()[B)
internal object AM {
  val MARKER_ACCEPT_MY: ByteArray = "__accept my__".toByteArray()
  val MARKER_ACCEPT_THEIRS: ByteArray = "__accept theirs__".toByteArray()
}

internal class GitTest : GitTestCase() {
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

  @Test fun pullToRepositoryWithoutCommits() {
    doPullToRepositoryWithoutCommits(null)
  }

  @Test fun pullToRepositoryWithoutCommitsAndCustomRemoteBranchName() {
    doPullToRepositoryWithoutCommits("customRemoteBranchName")
  }

  private fun doPullToRepositoryWithoutCommits(remoteBranchName: String?) {
    createLocalAndRemoteRepositories(remoteBranchName)
    repositoryManager.pull()
    compareFiles(repository.workTreePath, remoteRepository.workTreePath)
  }

  @Test fun pullToRepositoryWithCommits() {
    doPullToRepositoryWithCommits(null)
  }

  @Test fun pullToRepositoryWithCommitsAndCustomRemoteBranchName() {
    doPullToRepositoryWithCommits("customRemoteBranchName")
  }

  private fun doPullToRepositoryWithCommits(remoteBranchName: String?) {
    createLocalAndRemoteRepositories(remoteBranchName)
    val file = addAndCommit("local.xml")

    repositoryManager.commit()
    repositoryManager.pull()
    assertThat(repository.workTree.resolve(file.name)).hasBinaryContent(file.data)
    compareFiles(repository.workTreePath, remoteRepository.workTreePath, PathUtilRt.getFileName(file.name))
  }

  // never was merged. we reset using "merge with strategy "theirs", so, we must test - what's happen if it is not first merge? - see next test
  @Test fun resetToTheirsIfFirstMerge() {
    createLocalAndRemoteRepositories(initialCommit = true)

    sync(SyncType.OVERWRITE_LOCAL)
    fs
      .file(SAMPLE_FILE_NAME, SAMPLE_FILE_CONTENT)
      .compare()
  }

  @Test fun `overwrite local - second merge is null`() {
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

  @Test fun `merge - resolve conflicts to my`() {
    createLocalAndRemoteRepositories()

    val data = AM.MARKER_ACCEPT_MY
    provider.write(SAMPLE_FILE_NAME, data)

    sync(SyncType.MERGE)

    restoreRemoteAfterPush()
    fs.file(SAMPLE_FILE_NAME, data.toString(StandardCharsets.UTF_8)).compare()
  }

  @Test fun `merge - theirs file deleted, my modified, accept theirs`() {
    createLocalAndRemoteRepositories()

    sync(SyncType.MERGE)

    val data = AM.MARKER_ACCEPT_THEIRS
    provider.write(SAMPLE_FILE_NAME, data)
    repositoryManager.commit()

    remoteRepository.deletePath(SAMPLE_FILE_NAME)
    remoteRepository.commit("delete $SAMPLE_FILE_NAME")

    sync(SyncType.MERGE)

    fs.compare()
  }

  @Test fun `merge - my file deleted, theirs modified, accept my`() {
    createLocalAndRemoteRepositories()

    sync(SyncType.MERGE)

    provider.delete(SAMPLE_FILE_NAME)
    repositoryManager.commit()

    remoteRepository.writePath(SAMPLE_FILE_NAME, AM.MARKER_ACCEPT_THEIRS)
    remoteRepository.commit("")

    sync(SyncType.MERGE)
    restoreRemoteAfterPush()

    fs.compare()
  }

  @Test fun `commit if unmerged`() {
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

  @Test fun gitignore() {
    createLocalAndRemoteRepositories()

    provider.write(".gitignore", "*.html")
    sync(SyncType.MERGE)

    val filePaths = listOf("bar.html", "i/am/a/long/path/to/file/foo.html")
    for (path in filePaths) {
      provider.write(path, path)
    }

    val diff = repository.computeIndexDiff()
    assertThat(diff.diff()).isFalse()
    assertThat(diff.added).isEmpty()
    assertThat(diff.changed).isEmpty()
    assertThat(diff.removed).isEmpty()
    assertThat(diff.modified).isEmpty()
    assertThat(diff.untracked).isEmpty()
    assertThat(diff.untrackedFolders).isEmpty()

    for (path in filePaths) {
      assertThat(provider.read(path)).isNull()
    }
  }

  @Test fun `initial copy to repository - no local files`() {
    createRemoteRepository(initialCommit = false)
    // check error during findRemoteRefUpdatesFor (no master ref)
    testInitialCopy(false)
  }

  @Test fun `initial copy to repository - some local files`() {
    createRemoteRepository(initialCommit = false)
    // check error during findRemoteRefUpdatesFor (no master ref)
    testInitialCopy(true)
  }

  @Test fun `initial copy to repository - remote files removed`() {
    createRemoteRepository(initialCommit = true)

    // check error during findRemoteRefUpdatesFor (no master ref)
    testInitialCopy(true, SyncType.OVERWRITE_REMOTE)
  }

  private fun testInitialCopy(addLocalFiles: Boolean, syncType: SyncType = SyncType.MERGE) {
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
    store.storageManager.streamProvider = provider

    icsManager.sync(syncType, GitTestCase.projectRule.project, { copyLocalConfig(store.storageManager) })

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

  private fun doSyncWithUninitializedUpstream(syncType: SyncType) {
    createRemoteRepository(initialCommit = false)
    repositoryManager.setUpstream(remoteRepository.workTree.absolutePath)

    val path = "local.xml"
    val data = "<application />"
    provider.write(path, data)

    sync(syncType)

    if (syncType != SyncType.OVERWRITE_LOCAL) {
      fs.file(path, data)
    }
    restoreRemoteAfterPush();
    fs.compare()
  }
}