package org.jetbrains.plugins.settingsRepository

import com.intellij.mock.MockVirtualFileSystem
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.impl.stores.StreamProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.ArrayUtil
import com.intellij.util.PathUtilRt
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.jetbrains.plugins.settingsRepository.git.*
import org.junit.*

import javax.swing.*
import java.io.File
import java.util.Arrays
import java.util.Comparator

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.junit.Assert.assertThat

class GitTest {
  data class FileInfo (val name: String, val data: ByteArray)

  private var fixture: IdeaProjectTestFixture? = null

  private var remoteRepository: File? = null

  private val testWatcher = GitTestWatcher()

  Rule
  public fun getTestWatcher(): GitTestWatcher = testWatcher

  private var remoteRepositoryApi: Git? = null

  class object {
    private var ICS_DIR: File? = null

    {
      Logger.setFactory(javaClass<TestLoggerFactory>())
      PlatformTestCase.initPlatformLangPrefix()
    }

    private val repositoryManager: GitRepositoryManager
      get() = IcsManager.getInstance().repositoryManager as GitRepositoryManager

    private val repository: Repository
      get() = repositoryManager.repository

    private val testDataPath: String = PathManager.getHomePath() + "/settings-repository/testData"

    // BeforeClass doesn't work in Kotlin
    public fun setIcsDir() {
      val icsDirPath = System.getProperty("ics.settingsRepository")
      if (icsDirPath == null) {
        ICS_DIR = FileUtil.generateRandomTemporaryPath()
        System.setProperty("ics.settingsRepository", ICS_DIR!!.getAbsolutePath())
      }
      else {
        ICS_DIR = File(FileUtil.expandUserHome(icsDirPath))
        FileUtil.delete(ICS_DIR!!)
      }
    }

    private fun delete(data: ByteArray, directory: Boolean) {
      val addedFile = "\$APP_CONFIG$/remote.xml"
      getProvider().saveContent(addedFile, data, data.size, RoamingType.PER_USER, true)
      getProvider().delete(if (directory) "\$APP_CONFIG$" else addedFile, RoamingType.PER_USER)

      val diff = repository.computeIndexDiff()
      assertThat(diff.diff(), equalTo(false))
      assertThat(diff.getAdded(), empty())
      assertThat(diff.getChanged(), empty())
      assertThat(diff.getRemoved(), empty())
      assertThat(diff.getModified(), empty())
      assertThat(diff.getUntracked(), empty())
      assertThat(diff.getUntrackedFolders(), empty())
    }

    private fun getProvider(): StreamProvider {
      val provider = (ApplicationManager.getApplication() as ApplicationImpl).getStateStore().getStateStorageManager().getStreamProvider()
      assertThat(provider, notNullValue())
      return provider!!
    }

    private fun addAndCommit(path: String): FileInfo {
      val data = FileUtil.loadFileBytes(File(testDataPath, PathUtilRt.getFileName(path)))
      getProvider().saveContent(path, data, data.size, RoamingType.PER_USER, false)
      repositoryManager.commit(EmptyProgressIndicator())
      return FileInfo(path, data)
    }
  }

  Before
  public fun setUp() {
    if (ICS_DIR == null) {
      setIcsDir()
    }

    fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder().getFixture()
    SwingUtilities.invokeAndWait(object : Runnable {
      override fun run() {
        fixture!!.setUp()
      }
    })

    val icsManager = IcsManager.getInstance()
    (icsManager.repositoryManager as GitRepositoryManager).recreateRepository()
    icsManager.repositoryActive = true
  }

  After
  public fun tearDown() {
    remoteRepository = null
    remoteRepositoryApi = null

    IcsManager.getInstance().repositoryActive = false
    try {
      if (fixture != null) {
        SwingUtilities.invokeAndWait(object : Runnable {
          override fun run() {
            fixture!!.tearDown()
          }
        })
      }
    }
    finally {
      if (ICS_DIR != null) {
        FileUtil.delete(ICS_DIR!!)
      }
    }
  }

  Test
  public fun add() {
    val data = FileUtil.loadFileBytes(File(testDataPath, "remote.xml"))
    val addedFile = "\$APP_CONFIG$/remote.xml"
    getProvider().saveContent(addedFile, data, data.size, RoamingType.PER_USER, false)

    val diff = repository.computeIndexDiff()
    assertThat(diff.diff(), equalTo(true))
    assertThat(diff.getAdded(), contains(equalTo(addedFile)))
    assertThat(diff.getChanged(), empty())
    assertThat(diff.getRemoved(), empty())
    assertThat(diff.getModified(), empty())
    assertThat(diff.getUntracked(), empty())
    assertThat(diff.getUntrackedFolders(), empty())
  }

  Test
  public fun addSeveral() {
    val data = FileUtil.loadFileBytes(File(testDataPath, "remote.xml"))
    val data2 = FileUtil.loadFileBytes(File(testDataPath, "local.xml"))
    val addedFile = "\$APP_CONFIG$/remote.xml"
    val addedFile2 = "\$APP_CONFIG$/local.xml"
    getProvider().saveContent(addedFile, data, data.size, RoamingType.PER_USER, false)
    getProvider().saveContent(addedFile2, data2, data2.size, RoamingType.PER_USER, false)

    val diff = repository.computeIndexDiff()
    assertThat(diff.diff(), equalTo(true))
    assertThat(diff.getAdded(), contains(equalTo(addedFile), equalTo(addedFile2)))
    assertThat(diff.getChanged(), empty())
    assertThat(diff.getRemoved(), empty())
    assertThat(diff.getModified(), empty())
    assertThat(diff.getUntracked(), empty())
    assertThat(diff.getUntrackedFolders(), empty())
  }

  Test
  public fun delete() {
    val data = FileUtil.loadFileBytes(File(testDataPath, "remote.xml"))
    delete(data, false)
    delete(data, true)
  }

  Test
  public fun setUpstream() {
    val url = "https://github.com/user/repo.git"
    repositoryManager.setUpstream(url, null)
    assertThat(repositoryManager.getUpstream(), equalTo(url))
  }

  Test
  public fun pullToRepositoryWithoutCommits() {
    doPullToRepositoryWithoutCommits(null)
  }

  Test
  public fun pullToRepositoryWithoutCommitsAndCustomRemoteBranchName() {
    doPullToRepositoryWithoutCommits("customRemoteBranchName")
  }

  private fun doPullToRepositoryWithoutCommits(remoteBranchName: String?) {
    val remoteRepository = createFileRemote(remoteBranchName)
    repositoryManager.setUpstream(remoteRepository.getAbsolutePath(), remoteBranchName)
    repositoryManager.pull(EmptyProgressIndicator())
    compareFiles(repository.getWorkTree(), remoteRepository)
  }

  Test
  public fun pullToRepositoryWithCommits() {
    doPullToRepositoryWithCommits(null)
  }

  Test
  public fun pullToRepositoryWithCommitsAndCustomRemoteBranchName() {
    doPullToRepositoryWithCommits("customRemoteBranchName")
  }

  private fun doPullToRepositoryWithCommits(remoteBranchName: String?) {
    val file = createLocalRepositoryAndCommit(remoteBranchName)

    val progressIndicator = EmptyProgressIndicator()
    repositoryManager.commit(progressIndicator)
    repositoryManager.pull(progressIndicator)
    assertThat(FileUtil.loadFile(File(repository.getWorkTree(), file.name)), equalTo(String(file.data, CharsetToolkit.UTF8_CHARSET)))
    compareFiles(repository.getWorkTree(), remoteRepository!!, PathUtilRt.getFileName(file.name))
  }
  
  private fun createLocalRepositoryAndCommit(remoteBranchName: String?): FileInfo {
    remoteRepository = createFileRemote(remoteBranchName)
    repositoryManager.setUpstream(remoteRepository!!.getAbsolutePath(), remoteBranchName)

    return addAndCommit("\$APP_CONFIG$/local.xml")
  }

  // never was merged. we reset using "merge with strategy "theirs", so, we must test - what's happen if it is not first merge? - see next test
  Test
  public fun resetToTheirsIfFirstMerge() {
    createLocalRepositoryAndCommit(null)
    sync(SyncType.RESET_TO_THEIRS)
    val fs = MockVirtualFileSystem()
    fs.findFileByPath("\$APP_CONFIG$/remote.xml")
    compareFiles(repository.getWorkTree(), remoteRepository!!, fs.findFileByPath(""))
  }

  Test
  public fun resetToTheirsISecondMergeIsEmpty() {
    createLocalRepositoryAndCommit(null)
    sync(SyncType.MERGE)

    restoreRemoteAfterPush()

    val fs = MockVirtualFileSystem()

    fun testRemote() {
      fs.findFileByPath("\$APP_CONFIG$/local.xml")
      fs.findFileByPath("\$APP_CONFIG$/remote.xml")
      compareFiles(repository.getWorkTree(), remoteRepository!!, fs.findFileByPath(""))
    }
    testRemote()

    addAndCommit("_mac/local2.xml")
    sync(SyncType.RESET_TO_THEIRS)

    compareFiles(repository.getWorkTree(), remoteRepository!!, fs.findFileByPath(""))

    // test: merge and push to remote after such reset
    sync(SyncType.MERGE)

    restoreRemoteAfterPush()

    testRemote()
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
    remoteRepositoryApi!!.reset().setMode(ResetCommand.ResetType.HARD).call()
  }

  private fun sync(syncType: SyncType) {
    SwingUtilities.invokeAndWait(object : Runnable {
      override fun run() {
        IcsManager.getInstance().sync(syncType, fixture!!.getProject())
      }
    })
  }

  private fun createFileRemote(branchName: String?): File {
    val repository = testWatcher.getRepository(ICS_DIR!!)
    remoteRepositoryApi = Git(repository)

    if (branchName != null) {
      // jgit cannot checkout&create branch if no HEAD (no commits in our empty repository), so we create initial empty commit
      remoteRepositoryApi!!.commit().setMessage("").call()

      remoteRepositoryApi!!.checkout().setCreateBranch(true).setName(branchName).call()
    }

    val addedFile = "\$APP_CONFIG$/remote.xml"
    val workTree = repository.getWorkTree()
    FileUtil.copy(File(testDataPath, "remote.xml"), File(workTree, addedFile))
    repository.edit(AddFile(addedFile))
    remoteRepositoryApi!!.commit().setMessage("").call()
    return workTree
  }
}

private fun compareFiles(local: File, remote: File, vararg localExcludes: String) {
  compareFiles(local, remote, null, *localExcludes)
}

private fun compareFiles(local: File, remote: File, expected: VirtualFile?, vararg localExcludes: String) {
  var localFiles = local.list()!!
  var remoteFiles = remote.list()!!

  localFiles = ArrayUtil.remove(localFiles, Constants.DOT_GIT)
  remoteFiles = ArrayUtil.remove(remoteFiles, Constants.DOT_GIT)

  Arrays.sort(localFiles)
  Arrays.sort(remoteFiles)

  if (localExcludes.size != 0) {
    for (localExclude in localExcludes) {
      localFiles = ArrayUtil.remove(localFiles, localExclude)
    }
  }

  assertThat(localFiles, equalTo(remoteFiles))

  val expectedFiles: Array<VirtualFile>?
  if (expected == null) {
    expectedFiles = null
  }
  else {
    assertThat(expected.isDirectory(), equalTo(true))
    //noinspection UnsafeVfsRecursion
    expectedFiles = expected.getChildren()
    Arrays.sort(expectedFiles!!, object : Comparator<VirtualFile> {
      override fun compare(o1: VirtualFile, o2: VirtualFile): Int {
        return o1.getName().compareTo(o2.getName())
      }
    })

    for (i in 0..expectedFiles!!.size - 1) {
      assertThat(localFiles[i], equalTo(expectedFiles[i].getName()))
    }
  }

  for (i in 0..localFiles.size - 1) {
    val localFile = File(local, localFiles[i])
    val remoteFile = File(remote, remoteFiles[i])
    val expectedFile: VirtualFile?
    if (expectedFiles == null) {
      expectedFile = null
    }
    else {
      expectedFile = expectedFiles[i]
      assertThat(expectedFile!!.isDirectory(), equalTo(localFile.isDirectory()))
    }

    if (localFile.isFile()) {
      assertThat(FileUtil.loadFile(localFile), equalTo(FileUtil.loadFile(remoteFile)))
    }
    else {
      compareFiles(localFile, remoteFile, expectedFile, *localExcludes)
    }
  }
}