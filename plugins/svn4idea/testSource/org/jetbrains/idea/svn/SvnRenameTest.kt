// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn

import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.rollback.RollbackProgressListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.vcs.AbstractVcsTestCase
import com.intellij.util.TimeoutUtil
import org.jetbrains.annotations.NonNls
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.*

@NonNls
private const val LOG_SEPARATOR_START = "-------------"

/**
 * @author yole
 */
class SvnRenameTest : SvnTestCase() {
  override fun setUp() {
    super.setUp()

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
  }

  @Test
  fun testSimpleRename() {
    val a = createFileInCommand("a.txt", "test")
    checkin()

    renameFileInCommand(a, "b.txt")
    runAndVerifyStatusSorted("A + b.txt", "D a.txt")
  }

  // IDEADEV-18844
  @Test
  fun testRenameReplace() {
    val a = createFileInCommand("a.txt", "old")
    val aNew = createFileInCommand("aNew.txt", "new")
    checkin()

    renameFileInCommand(a, "aOld.txt")
    renameFileInCommand(aNew, "a.txt")
    runAndVerifyStatusSorted("A + aOld.txt", "D aNew.txt", "R + a.txt")
  }

  // IDEADEV-16251
  @Test
  fun testRenameAddedPackage() {
    val dir = createDirInCommand(myWorkingCopyDir, "child")
    createFileInCommand(dir, "a.txt", "content")
    renameFileInCommand(dir, "newchild")
    runAndVerifyStatusSorted("A newchild", "A newchild/a.txt")
  }

  // IDEADEV-8091
  @Test
  fun testDoubleRename() {
    val a = createFileInCommand("a.txt", "test")
    checkin()

    renameFileInCommand(a, "b.txt")
    renameFileInCommand(a, "c.txt")
    runAndVerifyStatusSorted("A + c.txt", "D a.txt")
  }

  // IDEADEV-15876
  @Test
  fun testRenamePackageWithChildren() {
    val child = prepareDirectoriesForRename()

    renameFileInCommand(child, "childnew")
    runAndVerifyStatusSorted("A + childnew", "D child", "D child/a.txt",
                             "D child/grandChild",
                             "D child/grandChild/b.txt")

    refreshVfs()   // wait for end of refresh operations initiated from SvnFileSystemListener
    changeListManager.ensureUpToDate(false)
    val changes = ArrayList(changeListManager.defaultChangeList.changes)
    assertEquals(4, changes.size.toLong())
    AbstractVcsTestCase.sortChanges(changes)
    verifyChange(changes[0], "child", "childnew")
    verifyChange(changes[1], "child" + File.separatorChar + "a.txt", "childnew" + File.separatorChar + "a.txt")
    verifyChange(changes[2], "child" + File.separatorChar + "grandChild", "childnew" + File.separatorChar + "grandChild")
    verifyChange(changes[3], "child" + File.separatorChar + "grandChild" + File.separatorChar + "b.txt",
                 "childnew" + File.separatorChar + "grandChild" + File.separatorChar + "b.txt")

    // there is no such directory any more
    /*VirtualFile oldChild = myWorkingCopyDir.findChild("child");
    if (oldChild == null) {
      refreshVfs();
      oldChild = myWorkingCopyDir.findChild("child");
    }
    Assert.assertEquals(FileStatus.DELETED, changeListManager.getStatus(oldChild));*/
  }

  private fun prepareDirectoriesForRename(): VirtualFile {
    val child = createDirInCommand(myWorkingCopyDir, "child")
    val grandChild = createDirInCommand(child, "grandChild")
    createFileInCommand(child, "a.txt", "a")
    createFileInCommand(grandChild, "b.txt", "b")
    checkin()
    return child
  }

  // IDEADEV-19065
  @Test
  fun testCommitAfterRenameDir() {
    val child = prepareDirectoriesForRename()

    renameFileInCommand(child, "newchild")
    checkin()

    val runResult = runSvn("log", "-q", "newchild/a.txt")
    AbstractVcsTestCase.verify(runResult)
    val lines = StringUtil.split(runResult.stdout, "\n")
    val iterator = lines.iterator()
    while (iterator.hasNext()) {
      val next = iterator.next()
      if (next.startsWith(LOG_SEPARATOR_START)) {
        iterator.remove()
      }
    }
    assertEquals(2, lines.size.toLong())
    assertTrue(lines[0].startsWith("r2 |"))
    assertTrue(lines[1].startsWith("r1 |"))
  }

  // todo - undo; undo after commit
  // IDEADEV-9755
  @Test
  fun testRollbackRenameDir() {
    val child = prepareDirectoriesForRename()
    renameFileInCommand(child, "newchild")

    changeListManager.ensureUpToDate(false)
    val change = changeListManager.getChange(myWorkingCopyDir.findChild("newchild")!!)
    assertNotNull(change)

    val exceptions = ArrayList<VcsException>()
    vcs.rollbackEnvironment!!.rollbackChanges(listOf(change!!), exceptions, RollbackProgressListener.EMPTY)
    assertTrue(exceptions.isEmpty())
    assertFalse(File(myWorkingCopyDir.path, "newchild").exists())
    assertTrue(File(myWorkingCopyDir.path, "child").exists())
  }

  // todo undo; undo after commit
  // IDEADEV-7697
  @Test
  fun testMovePackageToParent() {
    val child = createDirInCommand(myWorkingCopyDir, "child")
    val grandChild = createDirInCommand(child, "grandChild")
    createFileInCommand(grandChild, "a.txt", "a")
    checkin()
    moveFileInCommand(grandChild, myWorkingCopyDir)
    refreshVfs()   // wait for end of refresh operations initiated from SvnFileSystemListener
    changeListManager.ensureUpToDate(false)
    val changes = ArrayList(changeListManager.defaultChangeList.changes)
    assertEquals(listToString(changes), 2, changes.size.toLong())
    AbstractVcsTestCase.sortChanges(changes)
    verifyChange(changes[0], "child" + File.separatorChar + "grandChild", "grandChild")
    verifyChange(changes[1], "child" + File.separatorChar + "grandChild" + File.separatorChar + "a.txt",
                 "grandChild" + File.separatorChar + "a.txt")
  }

  private fun listToString(changes: List<Change>): String {
    return "{" + StringUtil.join(changes, StringUtil.createToStringFunction(Change::class.java), ",") + "}"
  }

  // IDEADEV-19223
  @Test
  fun testRollbackRenameWithUnversioned() {
    val child = createDirInCommand(myWorkingCopyDir, "child")
    createFileInCommand(child, "a.txt", "a")
    checkin()
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    val unversioned = createFileInCommand(child, "u.txt", "u")
    val unversionedDir = createDirInCommand(child, "uc")
    createFileInCommand(unversionedDir, "c.txt", "c")

    changeListManager.ensureUpToDate(false)
    assertEquals(FileStatus.UNKNOWN, changeListManager.getStatus(unversioned))

    renameFileInCommand(child, "newchild")
    val childPath = File(myWorkingCopyDir.path, "child")
    val newChildPath = File(myWorkingCopyDir.path, "newchild")
    assertTrue(File(newChildPath, "a.txt").exists())
    assertTrue(File(newChildPath, "u.txt").exists())
    assertFalse(File(childPath, "u.txt").exists())

    refreshVfs()
    changeListManager.ensureUpToDate(false)
    val changes = ArrayList<Change>()
    changes.add(changeListManager.getChange(myWorkingCopyDir.findChild("newchild")!!.findChild("a.txt")!!)!!)
    changes.add(changeListManager.getChange(myWorkingCopyDir.findChild("newchild")!!)!!)

    val exceptions = ArrayList<VcsException>()
    vcs.rollbackEnvironment!!.rollbackChanges(changes, exceptions, RollbackProgressListener.EMPTY)
    TimeoutUtil.sleep(300)
    assertTrue(exceptions.isEmpty())
    val fileA = File(childPath, "a.txt")
    assertTrue(fileA.absolutePath, fileA.exists())
    val fileU = File(childPath, "u.txt")
    assertTrue(fileU.absolutePath, fileU.exists())
    val unversionedDirFile = File(childPath, "uc")
    assertTrue(unversionedDirFile.exists())
    assertTrue(File(unversionedDirFile, "c.txt").exists())
  }

  // IDEA-13824
  @Test
  fun testRenameFileRenameDir() {
    val child = prepareDirectoriesForRename()
    val f = child.findChild("a.txt")
    renameFileInCommand(f, "anew.txt")
    renameFileInCommand(child, "newchild")

    runAndVerifyStatusSorted("A + newchild", "A + newchild/anew.txt",
                             "D child", "D child/a.txt", "D child/grandChild",
                             "D child/grandChild/b.txt",
                             "D + newchild/a.txt")

    refreshVfs()   // wait for end of refresh operations initiated from SvnFileSystemListener
    changeListManager.ensureUpToDate(false)
    val changes = ArrayList(changeListManager.defaultChangeList.changes)
    val list = vcs.checkinEnvironment!!.commit(changes, "test")
    assertEquals(0, list!!.size.toLong())
  }

  // IDEADEV-19364
  @Test
  fun testUndoMovePackage() {
    val parent1 = createDirInCommand(myWorkingCopyDir, "parent1")
    val parent2 = createDirInCommand(myWorkingCopyDir, "parent2")
    val child = createDirInCommand(parent1, "child")
    createFileInCommand(child, "a.txt", "a")
    checkin()

    moveFileInCommand(child, parent2)
    undo()
    val childPath = File(parent1.path, "child")
    assertTrue(childPath.exists())
    assertTrue(File(childPath, "a.txt").exists())
  }

  // IDEADEV-19552
  @Test
  fun testUndoRename() {
    val file = createFileInCommand(myWorkingCopyDir, "a.txt", "A")
    checkin()

    renameFileInCommand(file, "b.txt")
    undo()
    assertTrue(File(myWorkingCopyDir.path, "a.txt").exists())
    assertFalse(File(myWorkingCopyDir.path, "b.txt").exists())
  }

  @Test
  fun testUndoCommittedRenameFile() {
    val file = createFileInCommand(myWorkingCopyDir, "a.txt", "A")
    checkin()

    renameFileInCommand(file, "b.txt")
    checkin()
    undo()
    runAndVerifyStatusSorted("A + a.txt", "D b.txt")
  }

  // IDEADEV-19336
  @Test
  fun testUndoMoveCommittedPackage() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE)
    val parent1 = createDirInCommand(myWorkingCopyDir, "parent1")
    val parent2 = createDirInCommand(myWorkingCopyDir, "parent2")
    val child = createDirInCommand(parent1, "child")
    createFileInCommand(child, "a.txt", "a")
    checkin()

    moveFileInCommand(child, parent2)
    checkin()

    undo()
    runAndVerifyStatusSorted("A + parent1/child", "D parent2/child", "D parent2/child/a.txt")
  }

  @Test
  fun testMoveToUnversioned() {
    val file = createFileInCommand(myWorkingCopyDir, "a.txt", "A")
    val child = moveToNewPackage(file, "child")
    runAndVerifyStatusSorted("A child", "A child/a.txt")
    checkin()
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    val unversioned = createDirInCommand(myWorkingCopyDir, "unversioned")
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    runAndVerifyStatusSorted("? unversioned")

    moveFileInCommand(child, unversioned)
    runAndVerifyStatusSorted("? unversioned", "D child", "D child/a.txt")
  }

  @Test
  fun testUndoMoveToUnversioned() {
    val file = createFileInCommand(myWorkingCopyDir, "a.txt", "A")
    val child = moveToNewPackage(file, "child")
    runAndVerifyStatusSorted("A child", "A child/a.txt")
    checkin()
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    val unversioned = createDirInCommand(myWorkingCopyDir, "unversioned")
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    runAndVerifyStatusSorted("? unversioned")

    moveFileInCommand(child, unversioned)
    runAndVerifyStatusSorted("? unversioned", "D child", "D child/a.txt")

    undo()
    runAndVerifyStatusSorted("? unversioned")
  }

  @Test
  fun testUndoMoveUnversionedToUnversioned() {
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    val file = createFileInCommand(myWorkingCopyDir, "a.txt", "A")
    runAndVerifyStatusSorted("? a.txt")
    val unversioned = createDirInCommand(myWorkingCopyDir, "unversioned")
    moveFileInCommand(file, unversioned)
    runAndVerifyStatusSorted("? unversioned")
    undo()
    runAndVerifyStatusSorted("? a.txt", "? unversioned")
  }

  @Test
  fun testUndoMoveAddedToUnversioned() {
    val file = createFileInCommand(myWorkingCopyDir, "a.txt", "A")
    runAndVerifyStatusSorted("A a.txt")
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    val unversioned = createDirInCommand(myWorkingCopyDir, "unversioned")
    moveFileInCommand(file, unversioned)
    runAndVerifyStatusSorted("? unversioned")
    undo()
    runAndVerifyStatusSorted("? a.txt", "? unversioned")
  }

  @Test
  fun testUndoMoveToUnversionedCommitted() {
    val file = createFileInCommand(myWorkingCopyDir, "a.txt", "A")
    val child = moveToNewPackage(file, "child")
    runAndVerifyStatusSorted("A child", "A child/a.txt")
    checkin()
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    val unversioned = createDirInCommand(myWorkingCopyDir, "unversioned")
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD)
    runAndVerifyStatusSorted("? unversioned")

    moveFileInCommand(child, unversioned)
    runAndVerifyStatusSorted("? unversioned", "D child", "D child/a.txt")
    checkin()

    undo()
    runAndVerifyStatusSorted("? child", "? unversioned")
  }

  // IDEA-92941
  @Test
  fun testUndoNewMove() {
    val sink = createDirInCommand(myWorkingCopyDir, "sink")
    val child = createDirInCommand(myWorkingCopyDir, "child")
    runAndVerifyStatusSorted("A child", "A sink")
    checkin()
    val file = createFileInCommand(child, "a.txt", "A")
    runAndVerifyStatusSorted("A child/a.txt")
    moveFileInCommand(file, sink)
    runAndVerifyStatusSorted("A sink/a.txt")
    undo()
    runAndVerifyStatusSorted("A child/a.txt")
  }

  // todo undo, undo committed?
  @Test
  fun testMoveToNewPackage() {
    val file = createFileInCommand(myWorkingCopyDir, "a.txt", "A")
    moveToNewPackage(file, "child")
    runAndVerifyStatusSorted("A child", "A child/a.txt")
  }

  @Test
  fun testMoveToNewPackageCommitted() {
    val file = createFileInCommand(myWorkingCopyDir, "a.txt", "A")
    checkin()
    moveToNewPackage(file, "child")
    runAndVerifyStatusSorted("A child", "A + child/a.txt", "D a.txt")
  }

  private fun moveToNewPackage(file: VirtualFile, packageName: String): VirtualFile {
    return writeCommandAction(myProject).compute<VirtualFile, IOException> {
      val packageDirectory = myWorkingCopyDir.createChildDirectory(this, packageName)
      file.move(this, packageDirectory)
      packageDirectory
    }
  }
}
