package org.jetbrains.settingsRepository.test

import com.intellij.mock.MockVirtualFileSystem
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtilRt
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.jetbrains.jgit.dirCache.deletePath
import org.jetbrains.jgit.dirCache.writePath
import org.jetbrains.settingsRepository.AM
import org.jetbrains.settingsRepository.SyncType
import org.jetbrains.settingsRepository.git.commit
import org.jetbrains.settingsRepository.git.computeIndexDiff
import org.jetbrains.settingsRepository.git.resetHard
import org.jetbrains.settingsRepository.icsManager
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File
import javax.swing.SwingUtilities

class GitTest : TestCase() {
  public Test fun add() {
    val data = FileUtil.loadFileBytes(File(testDataPath, "remote.xml"))
    val addedFile = "\$APP_CONFIG$/remote.xml"
    save(addedFile, data)

    val diff = repository.computeIndexDiff()
    assertThat(diff.diff(), equalTo(true))
    assertThat(diff.getAdded(), contains(equalTo(addedFile)))
    assertThat(diff.getChanged(), empty())
    assertThat(diff.getRemoved(), empty())
    assertThat(diff.getModified(), empty())
    assertThat(diff.getUntracked(), empty())
    assertThat(diff.getUntrackedFolders(), empty())
  }

  public Test fun addSeveral() {
    val data = FileUtil.loadFileBytes(File(testDataPath, "remote.xml"))
    val data2 = FileUtil.loadFileBytes(File(testDataPath, "local.xml"))
    val addedFile = "\$APP_CONFIG$/remote.xml"
    val addedFile2 = "\$APP_CONFIG$/local.xml"
    save(addedFile, data)
    save(addedFile2, data2)

    val diff = repository.computeIndexDiff()
    assertThat(diff.diff(), equalTo(true))
    assertThat(diff.getAdded(), contains(equalTo(addedFile), equalTo(addedFile2)))
    assertThat(diff.getChanged(), empty())
    assertThat(diff.getRemoved(), empty())
    assertThat(diff.getModified(), empty())
    assertThat(diff.getUntracked(), empty())
    assertThat(diff.getUntrackedFolders(), empty())
  }

  public Test fun delete() {
    val data = FileUtil.loadFileBytes(File(testDataPath, "remote.xml"))
    delete(data, false)
    delete(data, true)
  }

  public Test fun setUpstream() {
    val url = "https://github.com/user/repo.git"
    repositoryManager.setUpstream(url, null)
    assertThat(repositoryManager.getUpstream(), equalTo(url))
  }

  Test
  public fun pullToRepositoryWithoutCommits() {
    doPullToRepositoryWithoutCommits(null)
  }

  public Test fun pullToRepositoryWithoutCommitsAndCustomRemoteBranchName() {
    doPullToRepositoryWithoutCommits("customRemoteBranchName")
  }

  private fun doPullToRepositoryWithoutCommits(remoteBranchName: String?) {
    createLocalRepository(remoteBranchName)
    repositoryManager.pull(EmptyProgressIndicator())
    compareFiles(repository.getWorkTree(), remoteRepository.getWorkTree())
  }

  public Test fun pullToRepositoryWithCommits() {
    doPullToRepositoryWithCommits(null)
  }

  public Test fun pullToRepositoryWithCommitsAndCustomRemoteBranchName() {
    doPullToRepositoryWithCommits("customRemoteBranchName")
  }

  private fun doPullToRepositoryWithCommits(remoteBranchName: String?) {
    val file = createLocalRepositoryAndCommit(remoteBranchName)

    val progressIndicator = EmptyProgressIndicator()
    repositoryManager.commit(progressIndicator)
    repositoryManager.pull(progressIndicator)
    assertThat(FileUtil.loadFile(File(repository.getWorkTree(), file.name)), equalTo(String(file.data, CharsetToolkit.UTF8_CHARSET)))
    compareFiles(repository.getWorkTree(), remoteRepository.getWorkTree(), null, PathUtilRt.getFileName(file.name))
  }
  
  private fun createLocalRepository(remoteBranchName: String?) {
    createFileRemote(remoteBranchName)
    repositoryManager.setUpstream(remoteRepository.getWorkTree().getAbsolutePath(), remoteBranchName)
  }

  private fun createLocalRepositoryAndCommit(remoteBranchName: String?): FileInfo {
    createLocalRepository(remoteBranchName)
    return addAndCommit("\$APP_CONFIG$/local.xml")
  }

  private fun compareFiles(fs: MockVirtualFileSystem) {
    compareFiles(fs.getRoot())
  }

  private fun compareFiles(expected: VirtualFile?) {
    compareFiles(repository.getWorkTree(), remoteRepository.getWorkTree(), expected)
  }

  // never was merged. we reset using "merge with strategy "theirs", so, we must test - what's happen if it is not first merge? - see next test
  public Test fun resetToTheirsIfFirstMerge() {
    createLocalRepositoryAndCommit(null)
    sync(SyncType.OVERWRITE_LOCAL)
    compareFiles(fs("\$APP_CONFIG$/remote.xml"))
  }

  public Test fun resetToTheirsISecondMergeIsNull() {
    createLocalRepositoryAndCommit(null)
    sync(SyncType.MERGE)

    restoreRemoteAfterPush()

    val fs = MockVirtualFileSystem()

    fun testRemote() {
      fs.findFileByPath("\$APP_CONFIG$/local.xml")
      fs.findFileByPath("\$APP_CONFIG$/remote.xml")
      compareFiles(fs.getRoot())
    }
    testRemote()

    addAndCommit("_mac/local2.xml")
    sync(SyncType.OVERWRITE_LOCAL)

    compareFiles(fs.getRoot())

    // test: merge and push to remote after such reset
    sync(SyncType.MERGE)

    restoreRemoteAfterPush()

    testRemote()
  }

  public Test fun resetToMyIfFirstMerge() {
    createLocalRepositoryAndCommit(null)
    sync(SyncType.OVERWRITE_REMOTE)
    restoreRemoteAfterPush()
    compareFiles(fs("\$APP_CONFIG$/local.xml"))
  }

  public Test fun `reset to my, second merge is null`() {
    createLocalRepositoryAndCommit(null)
    sync(SyncType.MERGE)

    restoreRemoteAfterPush()

    val fs = fs("\$APP_CONFIG$/local.xml", "\$APP_CONFIG$/remote.xml")
    compareFiles(fs)

    val local2FilePath = "_mac/local2.xml"
    addAndCommit(local2FilePath)
    sync(SyncType.OVERWRITE_REMOTE)
    restoreRemoteAfterPush()

    fs.findFileByPath(local2FilePath)
    compareFiles(fs)

    // test: merge to remote after such reset
    sync(SyncType.MERGE)

    restoreRemoteAfterPush()

    compareFiles(fs)
  }

  public Test fun `merge - resolve conflicts to my`() {
    createLocalRepository(null)

    val data = AM.MARKER_ACCEPT_MY
    save("\$APP_CONFIG$/remote.xml", data)

    sync(SyncType.MERGE)

    restoreRemoteAfterPush()
    compareFiles(fs("\$APP_CONFIG$/remote.xml"))
  }

  public Test fun `merge - theirs file deleted, my modified, accept theirs`() {
    createLocalRepository(null)

    sync(SyncType.MERGE)

    val data = AM.MARKER_ACCEPT_THEIRS
    save("\$APP_CONFIG$/remote.xml", data)
    repositoryManager.commit(EmptyProgressIndicator())

    val remoteRepository = testHelper.repository!!
    remoteRepository.deletePath("\$APP_CONFIG$/remote.xml")
    remoteRepository.commit("delete remote.xml")

    sync(SyncType.MERGE)

    compareFiles(fs())
  }

  public Test fun `merge - my file deleted, theirs modified, accept my`() {
    createLocalRepository(null)

    sync(SyncType.MERGE)

    getProvider().delete("\$APP_CONFIG$/remote.xml", RoamingType.PER_USER)
    repositoryManager.commit(EmptyProgressIndicator())

    val remoteRepository = testHelper.repository!!
    remoteRepository.writePath("\$APP_CONFIG$/remote.xml", AM.MARKER_ACCEPT_THEIRS)
    remoteRepository.commit("")

    sync(SyncType.MERGE)
    restoreRemoteAfterPush()

    compareFiles(fs())
  }

  // remote is uninitialized (empty - initial commit is not done)
  public Test fun `merge with uninitialized upstream`() {
    doSyncWithUninitializedUpstream(SyncType.MERGE)
  }

  public Test fun `reset to my, uninitialized upstream`() {
    doSyncWithUninitializedUpstream(SyncType.OVERWRITE_REMOTE)
  }

  public Test fun `reset to theirs, uninitialized upstream`() {
    doSyncWithUninitializedUpstream(SyncType.OVERWRITE_LOCAL)
  }

  private fun doSyncWithUninitializedUpstream(syncType: SyncType) {
    createFileRemote(null, false)
    repositoryManager.setUpstream(remoteRepository.getWorkTree().getAbsolutePath(), null)

    val path = "\$APP_CONFIG$/local.xml"
    val data = FileUtil.loadFileBytes(File(testDataPath, PathUtilRt.getFileName(path)))
    save(path, data)

    sync(syncType)

    val fs = MockVirtualFileSystem()
    if (syncType != SyncType.OVERWRITE_LOCAL) {
      fs.findFileByPath(path)
    }
    restoreRemoteAfterPush();
    compareFiles(fs)
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
    testHelper.repository!!.resetHard()
  }

  private fun sync(syncType: SyncType) {
    SwingUtilities.invokeAndWait {
      icsManager.sync(syncType, fixture!!.getProject())
    }
  }
}