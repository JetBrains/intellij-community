// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableRunnable;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.join;
import static org.junit.Assert.*;

public class SvnCommitTest extends SvnTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
  }

  @Test
  public void testSimpleCommit() {
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "123");
    refreshChanges();

    checkinFile(file, FileStatus.ADDED);
  }

  @Test
  public void testCommitRename() {
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "123");
    refreshChanges();

    checkinFile(file, FileStatus.ADDED);

    renameFileInCommand(file, "aRenamed.txt");
    refreshChanges();

    checkinFile(file, FileStatus.MODIFIED);
  }

  @Test
  public void testRenameReplace() {
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "123");
    final VirtualFile file2 = createFileInCommand(myWorkingCopyDir, "aRenamed.txt", "1235");
    refreshChanges();

    checkinFiles(file, file2);

    renameFileInCommand(file, file.getName() + "7.txt");
    renameFileInCommand(file2, "a.txt");
    refreshChanges();

    checkinFiles(file, file2);
  }

  @Test
  public void testRenameFolder() {
    final VirtualFile dir = createDirInCommand(myWorkingCopyDir, "f");
    final VirtualFile file = createFileInCommand(dir, "a.txt", "123");
    final VirtualFile file2 = createFileInCommand(dir, "b.txt", "1235");
    refreshChanges();

    checkinFiles(dir, file, file2);

    renameFileInCommand(dir, dir.getName() + "dd");
    refreshChanges();

    checkinFiles(dir, file, file2);
  }

  @Test
  public void testCommitDeletion() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final VirtualFile dir = createDirInCommand(myWorkingCopyDir, "f");
    final VirtualFile file = createFileInCommand(dir, "a.txt", "123");
    final VirtualFile file2 = createFileInCommand(dir, "b.txt", "1235");
    refreshChanges();

    checkinFiles(dir, file, file2);

    final FilePath dirPath = VcsUtil.getFilePath(dir.getPath(), true);
    deleteFileInCommand(dir);
    refreshChanges();

    checkinPaths(dirPath);
  }

  @Test
  public void testSameRepoPlusInnerCopyCommit() throws Exception {
    assertCommitToOtherWorkingCopy(() -> prepareInnerCopy(false), "source/s1.txt", "source/inner1/inner2/inner/t11.txt");
  }

  @Test
  public void testAnotherRepoPlusInnerCopyCommit() throws Exception {
    assertCommitToOtherWorkingCopy(() -> prepareInnerCopy(true), "source/s1.txt", "source/inner1/inner2/inner/t11.txt");
  }

  @Test
  public void testPlusExternalCopyCommit() throws Exception {
    assertCommitToOtherWorkingCopy(() -> prepareExternal(), "source/s1.txt", "source/external/t11.txt");
  }

  private void assertCommitToOtherWorkingCopy(@NotNull ThrowableRunnable<Exception> workingCopyBuilder,
                                              @NotNull String path1,
                                              @NotNull String path2) throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    workingCopyBuilder.run();

    final File file1 = new File(myWorkingCopyDir.getPath(), path1);
    final File fileInner = new File(myWorkingCopyDir.getPath(), path2);
    assertTrue(file1.exists());
    assertTrue(fileInner.exists());
    final VirtualFile vf1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file1);
    final VirtualFile vf2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fileInner);
    assertNotNull(vf1);
    assertNotNull(vf2);

    editFileInCommand(vf1, "2317468732ghdwwe7y348rf");
    editFileInCommand(vf2, "2317468732ghdwwe7y348rf csdjcjksw");
    refreshChanges();

    HashSet<String> strings = checkinFiles(vf1, vf2);
    assertEquals(join(strings, "\n"), 1, strings.size());
  }

  private void checkinPaths(FilePath... files) {
    final List<Change> changes = new ArrayList<>();
    for (FilePath file : files) {
      final Change change = changeListManager.getChange(file);
      assertNotNull(change);
      changes.add(change);
    }
    final List<VcsException> exceptions = vcs.getCheckinEnvironment().commit(changes, "test comment list");
    assertTrue(exceptions == null || exceptions.isEmpty());
    refreshChanges();

    for (FilePath file : files) {
      final Change changeA = changeListManager.getChange(file);
      assertNull(changeA);
    }
  }

  private HashSet<String> checkinFiles(VirtualFile... files) {
    final List<Change> changes = new ArrayList<>();
    for (VirtualFile file : files) {
      final Change change = changeListManager.getChange(file);
      assertNotNull(change);
      changes.add(change);
    }
    final HashSet<String> feedback = new HashSet<>();
    final List<VcsException> exceptions = vcs.getCheckinEnvironment().commit(changes, "test comment list", o -> null, feedback);
    if (exceptions !=null && ! exceptions.isEmpty()) {
      exceptions.get(0).printStackTrace();
    }
    assertTrue(exceptions == null || exceptions.isEmpty());
    refreshChanges();

    for (VirtualFile file : files) {
      final Change changeA = changeListManager.getChange(file);
      assertNull(changeA);
    }
    return feedback;
  }

  protected void checkinFile(VirtualFile file, FileStatus status) {
    final Change change = changeListManager.getChange(file);
    assertNotNull(change);
    assertEquals(status, change.getFileStatus());
    final List<VcsException> exceptions = vcs.getCheckinEnvironment().commit(Collections.singletonList(change), "test comment");
    assertTrue(exceptions == null || exceptions.isEmpty());
    refreshChanges();
    final Change changeA = changeListManager.getChange(file);
    assertNull(changeA);
  }
}
