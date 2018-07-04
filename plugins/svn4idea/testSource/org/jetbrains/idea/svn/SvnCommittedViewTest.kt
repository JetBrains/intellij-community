// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn

import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil.getFilePath
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.directoryContent
import com.intellij.util.io.exists
import org.jetbrains.idea.svn.history.SvnChangeList
import org.jetbrains.idea.svn.history.SvnRepositoryLocation
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class SvnCommittedViewTest : SvnTestCase() {
  @Test
  fun testAdd() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE)

    val d1 = createDirInCommand(myWorkingCopyDir, "d1")
    val f11 = createFileInCommand(d1, "f11.txt", "123\n456")
    val f12 = createFileInCommand(d1, "f12.txt", "----")
    checkin()

    vcs.invokeRefreshSvnRoots()
    val committedChangesProvider = vcs.committedChangesProvider
    val changeListList = committedChangesProvider
      .getCommittedChanges(committedChangesProvider.createDefaultSettings(), SvnRepositoryLocation(myRepositoryUrl), 0)
    checkList(changeListList, 1, Data(absPath(f11), FileStatus.ADDED, null), Data(absPath(f12), FileStatus.ADDED, null),
              Data(absPath(d1), FileStatus.ADDED, null))
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

    vcs.invokeRefreshSvnRoots()
    val committedChangesProvider = vcs.committedChangesProvider
    val changeListList = committedChangesProvider
      .getCommittedChanges(committedChangesProvider.createDefaultSettings(), SvnRepositoryLocation(myRepositoryUrl), 0)
    checkList(changeListList, 2, Data(absPath(f11), FileStatus.DELETED, null))
    checkList(changeListList, 3, Data(absPath(d1), FileStatus.DELETED, null))
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

    vcs.invokeRefreshSvnRoots()
    val committedChangesProvider = vcs.committedChangesProvider
    val changeListList = committedChangesProvider
      .getCommittedChanges(committedChangesProvider.createDefaultSettings(), SvnRepositoryLocation(myRepositoryUrl), 0)
    checkList(changeListList, 2, Data(absPath(d1), FileStatus.MODIFIED, "- replaced"))
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

    vcs.invokeRefreshSvnRoots()
    val committedChangesProvider = vcs.committedChangesProvider
    val changeListList = committedChangesProvider
      .getCommittedChanges(committedChangesProvider.createDefaultSettings(), SvnRepositoryLocation(myRepositoryUrl), 0)
    checkList(changeListList, 2, Data(absPath(d1), FileStatus.MODIFIED, "- moved from .." + File.separatorChar))
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

    vcs.invokeRefreshSvnRoots()
    val committedChangesProvider = vcs.committedChangesProvider
    val changeListList = committedChangesProvider
      .getCommittedChanges(committedChangesProvider.createDefaultSettings(), SvnRepositoryLocation(myRepositoryUrl), 0)
    checkList(changeListList, 2, Data(absPath(d1), FileStatus.MODIFIED, "- moved from .." + File.separatorChar),
              Data(absPath(f11), FileStatus.MODIFIED, "- moved from $oldF11Path"))
  }

  @Test
  fun testCopyDir() {
    val trunk = createSubtree()
    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.toString(), "$myRepoUrl/trunk")
    runInAndVerifyIgnoreOutput("copy", "-m", "test", "$myRepoUrl/trunk", "$myRepoUrl/branch")

    vcs.invokeRefreshSvnRoots()
    val committedChangesProvider = vcs.committedChangesProvider
    val changeListList = committedChangesProvider
      .getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                           SvnRepositoryLocation(myRepositoryUrl.appendPath("branch", false)), 0)
    checkList(changeListList, 2,
              Data(File(myWorkingCopyDir.path, "branch").absolutePath, FileStatus.ADDED, "- copied from /trunk"))
  }

  @Test
  fun testCopyAndModify() {
    val trunk = createSubtree()
    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.toString(), "$myRepoUrl/trunk")

    update()

    runInAndVerifyIgnoreOutput("copy", myWorkingCopyDir.path + "/trunk", myWorkingCopyDir.path + "/branch")
    runInAndVerifyIgnoreOutput("propset", "testprop", "testval", myWorkingCopyDir.path + "/branch/folder")
    checkin()

    vcs.invokeRefreshSvnRoots()
    val committedChangesProvider = vcs.committedChangesProvider
    val changeListList = committedChangesProvider
      .getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                           SvnRepositoryLocation(myRepositoryUrl.appendPath("branch", false)), 0)
    checkList(changeListList, 2,
              Data(File(myWorkingCopyDir.path, "branch").absolutePath, FileStatus.ADDED, "- copied from /trunk"),
              Data(File(myWorkingCopyDir.path, "branch/folder").absolutePath, FileStatus.MODIFIED,
                   "- copied from /trunk/folder"))
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

  private fun absPath(vf: VirtualFile) = virtualToIoFile(vf).absolutePath

  private class Data(val myLocalPath: String, val myStatus: FileStatus, val myOriginText: String?) {
    fun shouldBeComparedWithChange(change: Change): Boolean {
      return if (FileStatus.DELETED == myStatus && change.afterRevision == null) {
        // before path
        change.beforeRevision != null && myLocalPath == change.beforeRevision!!.file.path
      }
      else {
        change.afterRevision != null && myLocalPath == change.afterRevision!!.file.path
      }
    }
  }

  private fun checkList(lists: List<SvnChangeList>, revision: Long, vararg content: Data) {
    var list: SvnChangeList? = null
    for (changeList in lists) {
      if (changeList.number == revision) {
        list = changeList
      }
    }
    assertNotNull("Change list #$revision not found.", list)

    val changes = ArrayList(list!!.changes)
    assertNotNull("Null changes list", changes)
    assertEquals(changes.size.toLong(), content.size.toLong())

    for (data in content) {
      var found = false
      for (change in changes) {
        if (data.shouldBeComparedWithChange(change)) {
          assertTrue(Comparing.equal(data.myOriginText, change.getOriginText(myProject)))
          assertEquals(data.myStatus, change.fileStatus)
          found = true
          break
        }
      }
      assertTrue(toString(data, changes), found)
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
