package org.jetbrains.settingsRepository.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.impl.stores.StreamProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.PathUtilRt
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.hamcrest.CoreMatchers
import org.hamcrest.Matchers
import org.jetbrains.jgit.dirCache.AddFile
import org.jetbrains.jgit.dirCache.edit
import org.jetbrains.settingsRepository.git.GitRepositoryManager
import org.jetbrains.settingsRepository.git.commit
import org.jetbrains.settingsRepository.git.computeIndexDiff
import org.jetbrains.settingsRepository.icsManager
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import java.io.File
import javax.swing.SwingUtilities

val testDataPath: String = "${PathManager.getHomePath()}/settings-repository/testData"

fun getProvider(): StreamProvider {
  val provider = (ApplicationManager.getApplication() as ApplicationImpl).getStateStore().getStateStorageManager().getStreamProvider()
  Assert.assertThat(provider, CoreMatchers.notNullValue())
  return provider!!
}

val repositoryManager: GitRepositoryManager
  get() = icsManager.repositoryManager as GitRepositoryManager

val repository: Repository
  get() = repositoryManager.repository


fun save(path: String, data: ByteArray) {
  getProvider().saveContent(path, data, data.size(), RoamingType.PER_USER)
}

fun addAndCommit(path: String): FileInfo {
  val data = FileUtil.loadFileBytes(File(testDataPath, PathUtilRt.getFileName(path)))
  save(path, data)
  repositoryManager.commit(EmptyProgressIndicator())
  return FileInfo(path, data)
}

fun delete(data: ByteArray, directory: Boolean) {
  val addedFile = "\$APP_CONFIG$/remote.xml"
  save(addedFile, data)
  getProvider().delete(if (directory) "\$APP_CONFIG$" else addedFile, RoamingType.PER_USER)

  val diff = repository.computeIndexDiff()
  Assert.assertThat(diff.diff(), CoreMatchers.equalTo(false))
  Assert.assertThat(diff.getAdded(), Matchers.empty())
  Assert.assertThat(diff.getChanged(), Matchers.empty())
  Assert.assertThat(diff.getRemoved(), Matchers.empty())
  Assert.assertThat(diff.getModified(), Matchers.empty())
  Assert.assertThat(diff.getUntracked(), Matchers.empty())
  Assert.assertThat(diff.getUntrackedFolders(), Matchers.empty())
}

abstract class TestCase {
  var fixture: IdeaProjectTestFixture? = null

  val testHelper = RespositoryHelper()

  Rule
  public fun getTestWatcher(): RespositoryHelper = testHelper

  val remoteRepository: Repository
    get() = testHelper.repository!!

  companion object {
    private var ICS_DIR: File? = null

    init {
      Logger.setFactory(javaClass<TestLoggerFactory>())
    }

    // BeforeClass doesn't work in Kotlin
    public fun setIcsDir() {
      val icsDirPath = System.getProperty("ics.settingsRepository")
      if (icsDirPath == null) {
        // we must not create file (i.e. this file doesn't exist)
        ICS_DIR = FileUtilRt.generateRandomTemporaryPath()
        System.setProperty("ics.settingsRepository", ICS_DIR!!.getAbsolutePath())
      }
      else {
        ICS_DIR = File(FileUtil.expandUserHome(icsDirPath))
        FileUtil.delete(ICS_DIR!!)
      }
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

    (icsManager.repositoryManager as GitRepositoryManager).createRepositoryIfNeed()
    icsManager.repositoryActive = true
  }

  After
  public fun tearDown() {
    icsManager.repositoryActive = false
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

  fun createFileRemote(branchName: String? = null, initialCommit: Boolean = true): File {
    val repository = testHelper.getRepository(ICS_DIR!!)
    if (branchName != null) {
      // jgit cannot checkout&create branch if no HEAD (no commits in our empty repository), so we create initial empty commit
      repository.commit("")
      Git(repository).checkout().setCreateBranch(true).setName(branchName).call()
    }

    val workTree: File = repository.getWorkTree()
    if (initialCommit) {
      val addedFile = "\$APP_CONFIG$/remote.xml"
      FileUtil.copy(File(testDataPath, "remote.xml"), File(workTree, addedFile))
      repository.edit(AddFile(addedFile))
      repository.commit("")
    }
    return workTree
  }
}