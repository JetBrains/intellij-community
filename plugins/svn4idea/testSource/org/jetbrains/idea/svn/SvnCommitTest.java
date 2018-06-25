// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public class SvnCommitTest extends SvnTestCase {
  private VcsDirtyScopeManager myDirtyScopeManager;
  private ChangeListManager myChangeListManager;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    myChangeListManager = ChangeListManager.getInstance(myProject);
  }

  @Test
  public void testSimpleCommit() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "123");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    checkinFile(file, FileStatus.ADDED);
  }

  @Test
  public void testCommitRename() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "123");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    checkinFile(file, FileStatus.ADDED);

    renameFileInCommand(file, "aRenamed.txt");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    checkinFile(file, FileStatus.MODIFIED);
  }

  @Test
  public void testRenameReplace() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "123");
    final VirtualFile file2 = createFileInCommand(myWorkingCopyDir, "aRenamed.txt", "1235");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    checkinFiles(file, file2);

    renameFileInCommand(file, file.getName() + "7.txt");
    renameFileInCommand(file2, "a.txt");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    checkinFiles(file, file2);
  }

  @Test
  public void testRenameFolder() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile dir = createDirInCommand(myWorkingCopyDir, "f");
    final VirtualFile file = createFileInCommand(dir, "a.txt", "123");
    final VirtualFile file2 = createFileInCommand(dir, "b.txt", "1235");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    checkinFiles(dir, file, file2);

    renameFileInCommand(dir, dir.getName() + "dd");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    checkinFiles(dir, file, file2);
  }

  @Test
  public void testCommitDeletion() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VirtualFile dir = createDirInCommand(myWorkingCopyDir, "f");
    final VirtualFile file = createFileInCommand(dir, "a.txt", "123");
    final VirtualFile file2 = createFileInCommand(dir, "b.txt", "1235");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    checkinFiles(dir, file, file2);

    final FilePath dirPath = VcsUtil.getFilePath(dir.getPath(), true);
    deleteFileInCommand(dir);

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    checkinPaths(dirPath);
  }

  @Test
  public void testSameRepoPlusInnerCopyCommit() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    prepareInnerCopy(false);
    final File file1 = new File(myWorkingCopyDir.getPath(), "source/s1.txt");
    final File fileInner = new File(myWorkingCopyDir.getPath(), "source/inner1/inner2/inner/t11.txt");

    assertTrue(file1.exists());
    assertTrue(fileInner.exists());
    final VirtualFile vf1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file1);
    final VirtualFile vf2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fileInner);
    assertNotNull(vf1);
    assertNotNull(vf2);

    editFileInCommand(vf1, "2317468732ghdwwe7y348rf");
    editFileInCommand(vf2, "2317468732ghdwwe7y348rf csdjcjksw");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final HashSet<String> strings = checkinFiles(vf1, vf2);
    System.out.println("" + StringUtil.join(strings, "\n"));
    assertEquals(1, strings.size());
  }

  @Test
  public void testAnotherRepoPlusInnerCopyCommit() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    prepareInnerCopy(true);
    final File file1 = new File(myWorkingCopyDir.getPath(), "source/s1.txt");
    final File fileInner = new File(myWorkingCopyDir.getPath(), "source/inner1/inner2/inner/t11.txt");

    assertTrue(file1.exists());
    assertTrue(fileInner.exists());
    final VirtualFile vf1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file1);
    final VirtualFile vf2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fileInner);
    assertNotNull(vf1);
    assertNotNull(vf2);

    editFileInCommand(vf1, "2317468732ghdwwe7y348rf");
    editFileInCommand(vf2, "2317468732ghdwwe7y348rf csdjcjksw");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    checkinFiles(vf1, vf2);
  }

  @Test
  public void testPlusExternalCopyCommit() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    prepareExternal();
    final File file1 = new File(myWorkingCopyDir.getPath(), "source/s1.txt");
    final File fileInner = new File(myWorkingCopyDir.getPath(), "source/external/t11.txt");

    assertTrue(file1.exists());
    assertTrue(fileInner.exists());
    final VirtualFile vf1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file1);
    final VirtualFile vf2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fileInner);
    assertNotNull(vf1);
    assertNotNull(vf2);

    editFileInCommand(vf1, "2317468732ghdwwe7y348rf");
    editFileInCommand(vf2, "2317468732ghdwwe7y348rf csdjcjksw");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    checkinFiles(vf1, vf2);
  }

  private void checkinPaths(FilePath... files) {
    final List<Change> changes = new ArrayList<>();
    for (FilePath file : files) {
      final Change change = myChangeListManager.getChange(file);
      assertNotNull(change);
      changes.add(change);
    }
    final List<VcsException> exceptions = vcs.getCheckinEnvironment().commit(changes, "test comment list");
    assertTrue(exceptions == null || exceptions.isEmpty());
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    for (FilePath file : files) {
      final Change changeA = myChangeListManager.getChange(file);
      assertNull(changeA);
    }
  }

  private HashSet<String> checkinFiles(VirtualFile... files) {
    final List<Change> changes = new ArrayList<>();
    for (VirtualFile file : files) {
      final Change change = myChangeListManager.getChange(file);
      assertNotNull(change);
      changes.add(change);
    }
    final HashSet<String> feedback = new HashSet<>();
    final List<VcsException> exceptions = vcs.getCheckinEnvironment().commit(changes, "test comment list", o -> null, feedback);
    if (exceptions !=null && ! exceptions.isEmpty()) {
      exceptions.get(0).printStackTrace();
    }
    assertTrue(exceptions == null || exceptions.isEmpty());
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    for (VirtualFile file : files) {
      final Change changeA = myChangeListManager.getChange(file);
      assertNull(changeA);
    }
    return feedback;
  }

  protected void checkinFile(VirtualFile file, FileStatus status) {
    final Change change = myChangeListManager.getChange(file);
    assertNotNull(change);
    assertEquals(status, change.getFileStatus());
    final List<VcsException> exceptions = vcs.getCheckinEnvironment().commit(Collections.singletonList(change), "test comment");
    assertTrue(exceptions == null || exceptions.isEmpty());
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    final Change changeA = myChangeListManager.getChange(file);
    assertNull(changeA);
  }
}
