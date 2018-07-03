// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn

import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsTestUtil
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.svn.history.SvnChangeList
import org.jetbrains.idea.svn.history.SvnRepositoryLocation
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.util.*

class SvnCommittedViewTest : SvnTestCase() {

  @Test
  @Throws(Exception::class)
  fun testAdd() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE)

    val d1 = createDirInCommand(myWorkingCopyDir, "d1")
    val f11 = createFileInCommand(d1, "f11.txt", "123\n456")
    val f12 = createFileInCommand(d1, "f12.txt", "----")

    // r1, addition without history
    checkin()

    vcs.invokeRefreshSvnRoots()
    val committedChangesProvider = vcs.committedChangesProvider
    val changeListList = committedChangesProvider
      .getCommittedChanges(committedChangesProvider.createDefaultSettings(), SvnRepositoryLocation(myRepositoryUrl), 0)
    checkList(changeListList, 1, Data(absPath(f11), FileStatus.ADDED, null), Data(absPath(f12), FileStatus.ADDED, null),
              Data(absPath(d1), FileStatus.ADDED, null))
  }

  @Test
  @Throws(Exception::class)
  fun testDelete() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE)

    val d1 = createDirInCommand(myWorkingCopyDir, "d1")
    val f11 = createFileInCommand(d1, "f11.txt", "123\n456")
    createFileInCommand(d1, "f12.txt", "----")

    // r1, addition without history
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
  @Throws(Exception::class)
  fun testReplaced() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE)

    val d1 = createDirInCommand(myWorkingCopyDir, "d1")
    createFileInCommand(d1, "f11.txt", "123\n456")
    createFileInCommand(d1, "f12.txt", "----")

    // r1, addition without history
    checkin()

    val dir = virtualToIoFile(d1)
    val d1Path = dir.absolutePath
    runInAndVerifyIgnoreOutput("delete", d1Path)
    val created = dir.mkdir()
    Assert.assertTrue(created)
    runInAndVerifyIgnoreOutput("add", d1Path)

    checkin()

    vcs.invokeRefreshSvnRoots()
    val committedChangesProvider = vcs.committedChangesProvider
    val changeListList = committedChangesProvider
      .getCommittedChanges(committedChangesProvider.createDefaultSettings(), SvnRepositoryLocation(myRepositoryUrl), 0)
    checkList(changeListList, 2, Data(absPath(d1), FileStatus.MODIFIED, "- replaced"))
  }

  @Test
  @Throws(Exception::class)
  fun testMoveDir() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE)

    val d1 = createDirInCommand(myWorkingCopyDir, "d1")
    val d2 = createDirInCommand(myWorkingCopyDir, "d2")
    createFileInCommand(d1, "f11.txt", "123\n456")
    createFileInCommand(d1, "f12.txt", "----")

    // r1, addition without history
    checkin()

    moveFileInCommand(d1, d2)
    Thread.sleep(100)

    checkin()

    vcs.invokeRefreshSvnRoots()
    val committedChangesProvider = vcs.committedChangesProvider
    val changeListList = committedChangesProvider
      .getCommittedChanges(committedChangesProvider.createDefaultSettings(), SvnRepositoryLocation(myRepositoryUrl), 0)
    checkList(changeListList, 2, Data(absPath(d1), FileStatus.MODIFIED, "- moved from .." + File.separatorChar))
  }

  @Test
  @Throws(Exception::class)
  fun testMoveDirChangeFile() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE)

    val d1 = createDirInCommand(myWorkingCopyDir, "d1")
    val d2 = createDirInCommand(myWorkingCopyDir, "d2")
    val f11 = createFileInCommand(d1, "f11.txt", "123\n456")
    createFileInCommand(d1, "f12.txt", "----")

    // r1, addition without history
    checkin()

    val oldF11Path = virtualToIoFile(f11).absolutePath
    moveFileInCommand(d1, d2)
    VcsTestUtil.editFileInCommand(myProject, f11, "new")

    Thread.sleep(100)

    checkin()

    vcs.invokeRefreshSvnRoots()
    val committedChangesProvider = vcs.committedChangesProvider
    val changeListList = committedChangesProvider
      .getCommittedChanges(committedChangesProvider.createDefaultSettings(), SvnRepositoryLocation(myRepositoryUrl), 0)
    checkList(changeListList, 2, Data(absPath(d1), FileStatus.MODIFIED, "- moved from .." + File.separatorChar),
              Data(absPath(f11), FileStatus.MODIFIED, "- moved from $oldF11Path"))
  }

  @Test
  @Throws(Exception::class)
  fun testCopyDir() {
    val trunk = File(myTempDirFixture.tempDirPath, "trunk")
    trunk.mkdir()
    Thread.sleep(100)
    val folder = File(trunk, "folder")
    folder.mkdir()
    Thread.sleep(100)
    File(folder, "f1.txt").createNewFile()
    File(folder, "f2.txt").createNewFile()
    Thread.sleep(100)

    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.absolutePath, "$myRepoUrl/trunk")
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
  @Throws(Exception::class)
  fun testCopyAndModify() {
    val trunk = File(myTempDirFixture.tempDirPath, "trunk")
    trunk.mkdir()
    Thread.sleep(100)
    val folder = File(trunk, "folder")
    folder.mkdir()
    Thread.sleep(100)
    File(folder, "f1.txt").createNewFile()
    File(folder, "f2.txt").createNewFile()
    Thread.sleep(100)

    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.absolutePath, "$myRepoUrl/trunk")

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

  protected fun absPath(vf: VirtualFile): String {
    return virtualToIoFile(vf).absolutePath
  }

  protected class Data(val myLocalPath: String, val myStatus: FileStatus, val myOriginText: String?) {

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

  protected fun checkList(lists: List<SvnChangeList>, revision: Long, vararg content: Data) {
    var list: SvnChangeList? = null
    for (changeList in lists) {
      if (changeList.number == revision) {
        list = changeList
      }
    }
    Assert.assertNotNull("Change list #$revision not found.", list)

    val changes = ArrayList(list!!.changes)
    Assert.assertNotNull("Null changes list", changes)
    Assert.assertEquals(changes.size.toLong(), content.size.toLong())

    for (data in content) {
      var found = false
      for (change in changes) {
        if (data.shouldBeComparedWithChange(change)) {
          Assert.assertTrue(Comparing.equal(data.myOriginText, change.getOriginText(myProject)))
          Assert.assertEquals(data.myStatus, change.fileStatus)
          found = true
          break
        }
      }
      Assert.assertTrue(printChanges(data, changes), found)
    }
  }

  private fun printChanges(data: Data, changes: Collection<Change>): String {
    val sb = StringBuilder("Data: ").append(data.myLocalPath).append(" exists: ").append(File(data.myLocalPath).exists()).append(
      " Changes: ")
    for (change in changes) {
      val cr = if (change.afterRevision == null) change.beforeRevision else change.afterRevision
      val ioFile = cr!!.file.ioFile
      sb.append("'").append(ioFile.absolutePath).append("' exists: ").append(ioFile.exists()).append(" | ")
    }
    return sb.toString()
  }
}
