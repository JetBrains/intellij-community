package org.jetbrains.settingsRepository.test

import com.intellij.testFramework.file
import com.intellij.util.directoryStreamIfExists
import com.intellij.util.readBytes
import org.jetbrains.settingsRepository.SyncType
import org.junit.Test

// empty means "no files, no HEAD, no commits"
internal class OverwriteRemote : GitTestCase() {
  @Test fun `not empty local and not empty remote`() {
    addLocalToFs()
    doTest(true)
  }

  @Test fun `not empty local and empty remote`() {
    addLocalToFs()
    doTest(false)
  }

  private fun addLocalToFs() {
    fs
      .file("local.xml", """<file path="local.xml" />""")
  }

  @Test fun `empty local`() {
    doTest(false)
  }

  @Test fun `empty local and empty remote`() {
    doTest(true)
  }

  private fun doTest(initialRemoteCommit: Boolean) {
    createRemoteRepository(initialCommit = initialRemoteCommit)
    configureLocalRepository()

    val root = fs.getPath("/")
    root.directoryStreamIfExists {
      for (path in it) {
        provider.write(path.toString().substring(1), path.readBytes())
      }
      repositoryManager.commit()
    }

    sync(SyncType.OVERWRITE_REMOTE)
    restoreRemoteAfterPush()
    fs.compare()
  }

  @Test fun `second merge is null`() {
    createLocalAndRemoteRepositories(initialCommit = true)

    sync(SyncType.MERGE)

    restoreRemoteAfterPush()

    val fs = fs
      .file("local.xml", """<file path="local.xml" />""")
      .file(SAMPLE_FILE_NAME, SAMPLE_FILE_CONTENT)
      .compare()

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
}