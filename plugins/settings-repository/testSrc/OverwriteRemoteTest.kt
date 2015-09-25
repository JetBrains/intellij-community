package org.jetbrains.settingsRepository.test

import com.intellij.testFramework.file
import org.jetbrains.settingsRepository.SyncType
import org.junit.Test

internal class OverwriteRemote : GitTestCase() {
  @Test fun `first merge`() {
    createLocalAndRemoteRepositories()
    addAndCommit("local.xml")

    sync(SyncType.OVERWRITE_REMOTE)
    restoreRemoteAfterPush()
    fs
      .file("local.xml", """<file path="local.xml" />""")
      .compare()
  }

  @Test fun `empty local repo (no files, no HEAD, no local commits)`() {
    createLocalAndRemoteRepositories()
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