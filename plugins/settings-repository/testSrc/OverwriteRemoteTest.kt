package org.jetbrains.settingsRepository.test

import com.intellij.testFramework.file
import org.jetbrains.settingsRepository.SyncType
import org.junit.Test

// empty means "no files, no HEAD, no commits"
internal class OverwriteRemote : GitTestCase() {
  @Test fun `first merge`() {
    createLocalAndRemoteRepositories()
    val localFile = addAndCommit("local.xml")

    sync(SyncType.OVERWRITE_REMOTE)
    restoreRemoteAfterPush()
    fs
      .file(localFile.name, localFile.data)
      .compare()
  }

  @Test fun `empty local repo`() {
    createLocalAndRemoteRepositories()

    sync(SyncType.OVERWRITE_REMOTE)
    restoreRemoteAfterPush()
    fs.compare()
  }

  @Test fun `empty local and remote repositories`() {
    createRemoteRepository(initialCommit = false)
    configureLocalRepository()

    sync(SyncType.OVERWRITE_REMOTE)
    restoreRemoteAfterPush()
    fs.compare()
  }

  @Test fun `second merge is null`() {
    createLocalAndRemoteRepositories()
    addAndCommit("local.xml")

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