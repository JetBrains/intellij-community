// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn

import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.UsefulTestCase.assertDoesntExist
import com.intellij.testFramework.UsefulTestCase.assertExists
import com.intellij.util.io.systemIndependentPath
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.FeatureMatcher
import org.hamcrest.Matcher
import org.hamcrest.Matchers.*
import org.jetbrains.annotations.NonNls
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Paths

@NonNls
private const val LOG_SEPARATOR_START = "-------------"

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
    runAndVerifyStatus(
      "D a.txt", "> moved to b.txt",
      "A + b.txt", "> moved from a.txt"
    )
  }

  // IDEADEV-18844
  @Test
  fun testRenameReplace() {
    val a = createFileInCommand("a.txt", "old")
    val aNew = createFileInCommand("aNew.txt", "new")
    checkin()

    renameFileInCommand(a, "aOld.txt")
    renameFileInCommand(aNew, "a.txt")
    runAndVerifyStatus(
      "R + a.txt", "> moved to aOld.txt", "> moved from aNew.txt",
      "D aNew.txt", "> moved to a.txt",
      "A + aOld.txt", "> moved from a.txt"
    )
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
    runAndVerifyStatus(
      "D a.txt", "> moved to c.txt",
      "A + c.txt", "> moved from a.txt"
    )
  }

  // IDEADEV-15876
  @Test
  fun testRenamePackageWithChildren() {
    val child = prepareDirectoriesForRename()

    renameFileInCommand(child, "childnew")
    runAndVerifyStatus(
      "D child", "> moved to childnew",
      "D child/a.txt",
      "D child/grandChild",
      "D child/grandChild/b.txt",
      "A + childnew", "> moved from child"
    )

    refreshVfs()   // wait for end of refresh operations initiated from SvnFileSystemListener
    changeListManager.ensureUpToDate()
    assertChanges(
      "child" to "childnew",
      "child/a.txt" to "childnew/a.txt",
      "child/grandChild" to "childnew/grandChild",
      "child/grandChild/b.txt" to "childnew/grandChild/b.txt"
    )
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
    verify(runResult)
    val lines = runResult.stdout.lines().filterNot { it.isEmpty() || it.startsWith(LOG_SEPARATOR_START) }
    assertThat(lines, contains(startsWith("r2 |"), startsWith("r1 |")))
  }

  // todo - undo; undo after commit
  // IDEADEV-9755
  @Test
  fun testRollbackRenameDir() {
    val child = prepareDirectoriesForRename()
    renameFileInCommand(child, "newchild")

    changeListManager.ensureUpToDate()
    val change = changeListManager.getChange(myWorkingCopyDir.findChild("newchild")!!)
    assertNotNull(change)

    rollback(listOf(change))
    assertDoesntExist(File(myWorkingCopyDir.path, "newchild"))
    assertExists(File(myWorkingCopyDir.path, "child"))
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
    changeListManager.ensureUpToDate()
    assertChanges("child/grandChild" to "grandChild", "child/grandChild/a.txt" to "grandChild/a.txt")
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

    changeListManager.ensureUpToDate()
    assertEquals(FileStatus.UNKNOWN, changeListManager.getStatus(unversioned))

    renameFileInCommand(child, "newchild")
    val childPath = File(myWorkingCopyDir.path, "child")
    val newChildPath = File(myWorkingCopyDir.path, "newchild")
    assertExists(File(newChildPath, "a.txt"))
    assertExists(File(newChildPath, "u.txt"))
    assertDoesntExist(File(childPath, "u.txt"))

    refreshVfs()
    changeListManager.ensureUpToDate()
    val changes = mutableListOf(
      changeListManager.getChange(myWorkingCopyDir.findChild("newchild")!!.findChild("a.txt")!!)!!,
      changeListManager.getChange(myWorkingCopyDir.findChild("newchild")!!)!!
    )

    rollback(changes)
    assertExists(File(childPath, "a.txt"))
    assertExists(File(childPath, "u.txt"))
    assertExists(File(childPath, "uc"))
    assertExists(File(childPath, "uc/c.txt"))
  }

  // IDEA-13824
  @Test
  fun testRenameFileRenameDir() {
    val child = prepareDirectoriesForRename()
    val f = child.findChild("a.txt")
    renameFileInCommand(f, "anew.txt")
    renameFileInCommand(child, "newchild")

    runAndVerifyStatus(
      "D child", "> moved to newchild",
      "D child/a.txt",
      "D child/grandChild",
      "D child/grandChild/b.txt",
      "A + newchild", "> moved from child",
      "D + newchild/a.txt", "> moved to newchild/anew.txt",
      "A + newchild/anew.txt", "> moved from newchild/a.txt"
    )

    refreshVfs()   // wait for end of refresh operations initiated from SvnFileSystemListener
    changeListManager.ensureUpToDate()
    commit(changeListManager.defaultChangeList.changes.toList(), "test")
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
    assertExists(childPath)
    assertExists(File(childPath, "a.txt"))
  }

  // IDEADEV-19552
  @Test
  fun testUndoRename() {
    val file = createFileInCommand(myWorkingCopyDir, "a.txt", "A")
    checkin()

    renameFileInCommand(file, "b.txt")
    undo()
    assertExists(File(myWorkingCopyDir.path, "a.txt"))
    assertDoesntExist(File(myWorkingCopyDir.path, "b.txt"))
  }

  @Test
  fun testUndoCommittedRenameFile() {
    val file = createFileInCommand(myWorkingCopyDir, "a.txt", "A")
    checkin()

    renameFileInCommand(file, "b.txt")
    checkin()
    undo()
    runAndVerifyStatus(
      "A + a.txt", "> moved from b.txt",
      "D b.txt", "> moved to a.txt"
    )
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
    runAndVerifyStatus(
      "A + parent1/child", "> moved from parent2/child",
      "D parent2/child", "> moved to parent1/child",
      "D parent2/child/a.txt"
    )
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
    runAndVerifyStatus(
      "D a.txt", "> moved to child/a.txt",
      "A child",
      "A + child/a.txt", "> moved from a.txt"
    )
  }

  private fun moveToNewPackage(file: VirtualFile, packageName: String) =
    writeCommandAction(myProject).compute<VirtualFile, IOException> {
      val packageDirectory = myWorkingCopyDir.createChildDirectory(this, packageName)
      file.move(this, packageDirectory)
      packageDirectory
    }

  private fun assertChanges(vararg expected: Pair<String?, String?>) {
    val changes = changeListManager.defaultChangeList.changes.toMutableList()
    assertThat(changes, containsInAnyOrder(expected.map { changeMatcher(it) }))
  }

  private fun changeMatcher(paths: Pair<String?, String?>) = allOf(beforePathMatcher(paths.first), afterPathMatcher(paths.second))

  private fun beforePathMatcher(beforePath: String?) =
    object : FeatureMatcher<Change, String?>(pathMatcher(beforePath), "before path", "before path") {
      override fun featureValueOf(actual: Change) = actual.beforeRevision?.file?.path
    }

  private fun afterPathMatcher(afterPath: String?) =
    object : FeatureMatcher<Change, String?>(pathMatcher(afterPath), "after path", "after path") {
      override fun featureValueOf(actual: Change) = actual.afterRevision?.file?.path
    }

  fun pathMatcher(path: String?): Matcher<in String?> = if (path == null) nullValue()
  else equalToIgnoringCase(Paths.get(myWorkingCopyDir.path).resolve(toSystemDependentName(path)).systemIndependentPath)
}
