// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn

import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil.getAfterPath
import com.intellij.openapi.vcs.changes.ChangesUtil.getFilePath
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.directoryContent
import com.intellij.util.io.exists
import org.jetbrains.idea.svn.history.SvnChangeList
import org.jetbrains.idea.svn.history.SvnRepositoryLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.fail

class SvnCommittedViewTest : SvnTestCase() {
  @Test
  fun testAdd() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE)

    val d1 = createDirInCommand(myWorkingCopyDir, "d1")
    val f11 = createFileInCommand(d1, "f11.txt", "123\n456")
    val f12 = createFileInCommand(d1, "f12.txt", "----")
    checkin()

    assertRevisions(
      r(1,
        Data(f11, FileStatus.ADDED, null),
        Data(f12, FileStatus.ADDED, null),
        Data(d1, FileStatus.ADDED, null)
      )
    )
  }

  @Test
  fun testDelete() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE)

    val d1 = createDirInCommand(myWorkingCopyDir, "d1")
    val f11 = createFileInCommand(d1, "f11.txt", "123\n456")
    createFileInCommand(d1, "f12.txt", "----")
    checkin()

    deleteFileInCommand(f11)
    checkin()

    update()

    deleteFileInCommand(d1)
    checkin()

    assertRevisions(
      r(2, Data(f11, FileStatus.DELETED, null)),
      r(3, Data(d1, FileStatus.DELETED, null))
    )
  }

  @Test
  fun testReplaced() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE)

    val d1 = createDirInCommand(myWorkingCopyDir, "d1")
    createFileInCommand(d1, "f11.txt", "123\n456")
    createFileInCommand(d1, "f12.txt", "----")
    checkin()

    val dir = virtualToIoFile(d1)
    val d1Path = dir.absolutePath
    runInAndVerifyIgnoreOutput("delete", d1Path)
    assertTrue(dir.mkdir())
    runInAndVerifyIgnoreOutput("add", d1Path)
    checkin()

    assertRevisions(
      r(2, Data(d1, FileStatus.MODIFIED, "- replaced"))
    )
  }

  @Test
  fun testMoveDir() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE)

    val d1 = createDirInCommand(myWorkingCopyDir, "d1")
    val d2 = createDirInCommand(myWorkingCopyDir, "d2")
    createFileInCommand(d1, "f11.txt", "123\n456")
    createFileInCommand(d1, "f12.txt", "----")
    checkin()

    moveFileInCommand(d1, d2)
    checkin()

    assertRevisions(
      r(2, Data(d1, FileStatus.MODIFIED, "- moved from .." + File.separatorChar))
    )
  }

  @Test
  fun testMoveDirChangeFile() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE)

    val d1 = createDirInCommand(myWorkingCopyDir, "d1")
    val d2 = createDirInCommand(myWorkingCopyDir, "d2")
    val f11 = createFileInCommand(d1, "f11.txt", "123\n456")
    createFileInCommand(d1, "f12.txt", "----")
    checkin()

    val oldF11Path = virtualToIoFile(f11).absolutePath
    moveFileInCommand(d1, d2)
    editFileInCommand(f11, "new")
    checkin()

    assertRevisions(
      r(2,
        Data(d1, FileStatus.MODIFIED, "- moved from .." + File.separatorChar),
        Data(f11, FileStatus.MODIFIED, "- moved from $oldF11Path")
      )
    )
  }

  @Test
  fun testCopyDir() {
    val trunk = createSubtree()
    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.toString(), "$myRepoUrl/trunk")
    runInAndVerifyIgnoreOutput("copy", "-m", "test", "$myRepoUrl/trunk", "$myRepoUrl/branch")

    assertRevisionsInPath(
      "branch",
      r(2, Data(File(myWorkingCopyDir.path, "branch"), FileStatus.ADDED, "- copied from /trunk"))
    )
  }

  @Test
  fun testCopyAndModify() {
    val trunk = createSubtree()
    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.toString(), "$myRepoUrl/trunk")

    update()

    runInAndVerifyIgnoreOutput("copy", myWorkingCopyDir.path + "/trunk", myWorkingCopyDir.path + "/branch")
    runInAndVerifyIgnoreOutput("propset", "testprop", "testval", myWorkingCopyDir.path + "/branch/folder")
    checkin()

    assertRevisionsInPath(
      "branch",
      r(2,
        Data(File(myWorkingCopyDir.path, "branch"), FileStatus.ADDED, "- copied from /trunk"),
        Data(File(myWorkingCopyDir.path, "branch/folder"), FileStatus.MODIFIED, "- copied from /trunk/folder")
      )
    )
  }

  private fun createSubtree(): Path {
    val path = Paths.get(myTempDirFixture.tempDirPath)
    directoryContent {
      dir("trunk") {
        dir("folder") {
          file("f1.txt")
          file("f2.txt")
        }
      }
    }.generate(path.toFile())

    return path.resolve("trunk")
  }

  private class Data(val myLocalPath: String, val myStatus: FileStatus, val myOriginText: String?) {
    constructor(file: VirtualFile, status: FileStatus, originText: String?) : this(file.path, status, originText)
    constructor(file: File, status: FileStatus, originText: String?) : this(file.systemIndependentPath, status, originText)

    fun isFor(change: Change) = myLocalPath == if (myStatus == FileStatus.DELETED) getFilePath(change).path else getAfterPath(change)?.path
  }

  private fun r(revision: Long, vararg changes: Data) = revision to changes.toList()

  private fun assertRevisions(vararg revisions: Pair<Long, List<Data>>) = assertRevisionsInPath("", *revisions)

  private fun assertRevisionsInPath(path: String, vararg revisions: Pair<Long, List<Data>>) {
    vcs.invokeRefreshSvnRoots()

    val provider = vcs.committedChangesProvider
    val changeLists = provider.getCommittedChanges(
      provider.createDefaultSettings(), SvnRepositoryLocation(myRepositoryUrl.appendPath(path, false)), 0)

    revisions.forEach { assertRevision(changeLists, it.first, it.second) }
  }

  private fun assertRevision(changeLists: List<SvnChangeList>, revision: Long, expectedChanges: List<Data>) {
    val changeList = changeLists.find { it.number == revision } ?: fail("Change list #$revision not found")
    val changes = changeList.changes.toList()
    assertEquals(expectedChanges.size, changes.size)

    for (expected in expectedChanges) {
      val change = changes.find { expected.isFor(it) } ?: fail(toString(expected, changes))

      assertEquals(expected.myOriginText, change.getOriginText(myProject))
      assertEquals(expected.myStatus, change.fileStatus)
    }
  }

  private fun toString(data: Data, changes: Collection<Change>): String {
    val changesInfo = changes.joinToString("\n") {
      val file = getFilePath(it).ioFile
      "'${file.absolutePath}' exists: ${file.exists()}"
    }
    return """
      |Data: ${data.myLocalPath} exists: ${Paths.get(data.myLocalPath).exists()}
      |Changes: $changesInfo
      """.trimMargin()
  }
}
