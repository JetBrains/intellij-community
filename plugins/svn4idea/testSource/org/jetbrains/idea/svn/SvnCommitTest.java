// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableRunnable;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.util.containers.ContainerUtil.map2Array;
import static org.junit.Assert.*;

public class SvnCommitTest extends SvnTestCase {
  @Override
  public void before() throws Exception {
    super.before();

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
  }

  @Test
  public void testSimpleCommit() {
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "123");
    refreshChanges();

    assertCommit(file, FileStatus.ADDED);
  }

  @Test
  public void testCommitRename() {
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "123");
    refreshChanges();

    assertCommit(file, FileStatus.ADDED);

    renameFileInCommand(file, "aRenamed.txt");
    refreshChanges();

    assertCommit(file, FileStatus.MODIFIED);
  }

  @Test
  public void testRenameReplace() {
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "123");
    final VirtualFile file2 = createFileInCommand(myWorkingCopyDir, "aRenamed.txt", "1235");
    refreshChanges();

    assertCommit(file, file2);

    renameFileInCommand(file, file.getName() + "7.txt");
    renameFileInCommand(file2, "a.txt");
    refreshChanges();

    assertCommit(file, file2);
  }

  @Test
  public void testRenameFolder() {
    final VirtualFile dir = createDirInCommand(myWorkingCopyDir, "f");
    final VirtualFile file = createFileInCommand(dir, "a.txt", "123");
    final VirtualFile file2 = createFileInCommand(dir, "b.txt", "1235");
    refreshChanges();

    assertCommit(dir, file, file2);

    renameFileInCommand(dir, dir.getName() + "dd");
    refreshChanges();

    assertCommit(dir, file, file2);
  }

  @Test
  public void testCommitDeletion() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final VirtualFile dir = createDirInCommand(myWorkingCopyDir, "f");
    final VirtualFile file = createFileInCommand(dir, "a.txt", "123");
    final VirtualFile file2 = createFileInCommand(dir, "b.txt", "1235");
    refreshChanges();

    assertCommit(dir, file, file2);

    final FilePath dirPath = VcsUtil.getFilePath(dir.getPath(), true);
    deleteFileInCommand(dir);
    refreshChanges();

    assertCommit(dirPath);
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

    final VirtualFile vf1 = myWorkingCopyDir.findFileByRelativePath(path1);
    final VirtualFile vf2 = myWorkingCopyDir.findFileByRelativePath(path2);
    assertNotNull(vf1);
    assertNotNull(vf2);

    editFileInCommand(vf1, "2317468732ghdwwe7y348rf");
    editFileInCommand(vf2, "2317468732ghdwwe7y348rf csdjcjksw");
    refreshChanges();

    Set<String> strings = assertCommit(vf1, vf2);
    assertEquals(join(strings, "\n"), 1, strings.size());
  }

  private void assertCommit(VirtualFile file, FileStatus status) {
    final Change change = changeListManager.getChange(file);
    assertNotNull(change);
    assertEquals(status, change.getFileStatus());

    assertCommit(file);
  }

  private Set<String> assertCommit(VirtualFile... files) {
    return assertCommit(map2Array(files, FilePath.class, (VirtualFile file) -> VcsUtil.getFilePath(file)));
  }

  private Set<String> assertCommit(FilePath... files) {
    final List<Change> changes = new ArrayList<>();
    for (FilePath file : files) {
      final Change change = changeListManager.getChange(file);
      assertNotNull(change);
      changes.add(change);
    }
    Set<String> feedback = commit(changes, "test comment list");
    refreshChanges();

    for (FilePath file : files) {
      assertNull(changeListManager.getChange(file));
    }

    return feedback;
  }
}
