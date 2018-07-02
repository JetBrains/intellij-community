// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.RollbackWorker;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.DuringChangeListManagerUpdateTestScheme;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class SvnChangesCorrectlyRefreshedTest extends SvnTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  @Test
  public void testModificationAndAfterRevert() throws Exception {
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();
    sleep(100);

    VcsTestUtil.editFileInCommand(myProject, subTree.myS1File, "new content");

    final CharSequence text1 = LoadTextUtil.loadText(subTree.myS1File);
    Assert.assertEquals("new content", text1.toString());

    sleep(100);
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(virtualToIoFile(subTree.myS1File));
    refreshChanges();
    final VcsException updateException = changeListManager.getUpdateException();
    if (updateException != null) {
      updateException.printStackTrace();
    }
    if (! RepeatSvnActionThroughBusy.ourBusyExceptionProcessor.process(updateException)) {
      Assert.assertNull(updateException == null ? null : updateException.getMessage(), updateException);
    }

    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[]{subTree.myS1File}, changeListManager.getDefaultListName(),
                                                                changeListManager);

    final Collection<Change> changes = changeListManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject);
    worker.doRollback(changes, false);

    final CharSequence text = LoadTextUtil.loadText(subTree.myS1File);
    Assert.assertEquals(SubTree.ourS1Contents, text.toString());

    refreshChanges();
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VirtualFile.EMPTY_ARRAY, changeListManager.getDefaultListName(),
                                                                changeListManager);
  }

  @Test
  public void testRenameFileAndAfterRevert() throws Throwable {
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();

    final String newName = "newName";
    renameFileInCommand(subTree.myS1File, newName);

    assertVF(subTree.mySourceDir, newName);

    sleep(300);
    refreshChanges();
    DuringChangeListManagerUpdateTestScheme
      .checkFilesAreInList(new VirtualFile[]{subTree.myS1File}, changeListManager.getDefaultListName(), changeListManager);

    final Collection<Change> changes = changeListManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject);
    worker.doRollback(changes, false);

    assertVF(subTree.mySourceDir, "s1.txt");

    changeListManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VirtualFile.EMPTY_ARRAY, changeListManager.getDefaultListName(),
                                                                changeListManager);
  }

  @Test
  public void testMoveFileAndAfterRevert() throws Throwable {
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();

    moveFileInCommand(subTree.myS1File, subTree.myTargetDir);

    assertVF(subTree.myTargetDir, "s1.txt");

    sleep(300);
    refreshChanges();
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[]{subTree.myS1File}, changeListManager.getDefaultListName(),
                                                                changeListManager);

    final Collection<Change> changes = changeListManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject);
    worker.doRollback(changes, false);

    assertVF(subTree.mySourceDir, "s1.txt");

    changeListManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VirtualFile.EMPTY_ARRAY, changeListManager.getDefaultListName(),
                                                                changeListManager);
  }

  @Test
  public void testRenameDirAndAfterRevert() throws Throwable {
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();

    final String newName = "newName";
    renameFileInCommand(subTree.mySourceDir, newName);

    assertVF(subTree.myRootDir, newName);
    assertVF(subTree.mySourceDir, "s1.txt");
    assertVF(subTree.mySourceDir, "s2.txt");

    sleep(300);
    refreshChanges();
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.mySourceDir, subTree.myS1File, subTree.myS2File},
                                                                changeListManager.getDefaultListName(), changeListManager);

    final Collection<Change> changes = changeListManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject);
    worker.doRollback(changes, false);

    subTree.mySourceDir = assertVF(subTree.myRootDir, "source");
    Assert.assertTrue(subTree.mySourceDir.getPath().endsWith("/root/source"));
    assertVF(subTree.mySourceDir, "s1.txt");
    assertVF(subTree.mySourceDir, "s2.txt");

    changeListManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VirtualFile.EMPTY_ARRAY, changeListManager.getDefaultListName(),
                                                                changeListManager);
  }

  @Test
  public void testMoveDirEditFileAndAfterRevert() throws Throwable {
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();

    moveFileInCommand(subTree.mySourceDir, subTree.myTargetDir);
    Assert.assertTrue(subTree.mySourceDir.getPath().endsWith("/target/source"));
    assertVF(subTree.myTargetDir, "source");

    VcsTestUtil.editFileInCommand(myProject, subTree.myS1File, "new");
    final CharSequence text1 = LoadTextUtil.loadText(subTree.myS1File);
    Assert.assertEquals("new", text1.toString());

    sleep(300);
    refreshChanges();
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.mySourceDir, subTree.myS1File, subTree.myS2File},
                                                                changeListManager.getDefaultListName(), changeListManager);

    final Collection<Change> changes = changeListManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject);
    worker.doRollback(changes, false);

    subTree.mySourceDir = assertVF(subTree.myRootDir, "source");
    Assert.assertTrue(subTree.mySourceDir.getPath().endsWith("/root/source"));
    
    subTree.myS1File = assertVF(subTree.mySourceDir, "s1.txt");
    subTree.myS2File = assertVF(subTree.mySourceDir, "s2.txt");
    final CharSequence text = LoadTextUtil.loadText(subTree.myS1File);
    Assert.assertEquals(SubTree.ourS1Contents, text.toString());

    changeListManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VirtualFile.EMPTY_ARRAY, changeListManager.getDefaultListName(),
                                                                changeListManager);
  }
  
  @Test
  public void testAddDirEditFileAndAfterRevert() {
    final SubTree subTree = new SubTree(myWorkingCopyDir);

    refreshChanges();
    final List<VirtualFile> files = getAllFiles(subTree);

    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VfsUtil.toVirtualFileArray(files), changeListManager.getDefaultListName(),
                                                                changeListManager);

    final Collection<Change> changes = changeListManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject);
    worker.doRollback(changes, false);

    assertVF(subTree.myRootDir, "source");
    assertVF(subTree.mySourceDir, "s1.txt");
    assertVF(subTree.myRootDir, "target");

    changeListManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VirtualFile.EMPTY_ARRAY, changeListManager.getDefaultListName(),
                                                                changeListManager);

    for (VirtualFile file : files) {
      Assert.assertTrue(file.getPath(), changeListManager.isUnversioned(file));
    }
  }

  private List<VirtualFile> getAllFiles(final SubTree subTree) {
    final List<VirtualFile> files = new ArrayList<>();
    files.addAll(Arrays.asList(subTree.myRootDir, subTree.mySourceDir, subTree.myS2File, subTree.myS1File, subTree.myTargetDir));
    files.addAll(subTree.myTargetFiles);
    return files;
  }

  @Test
  public void testDeleteDirEditFileAndAfterRevert() throws Throwable {
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();

    deleteFileInCommand(subTree.myRootDir);
    sleep(300);

    refreshChanges();
    final List<VirtualFile> files = getAllFiles(subTree);
    DuringChangeListManagerUpdateTestScheme
      .checkDeletedFilesAreInList(VfsUtil.toVirtualFileArray(files), changeListManager.getDefaultListName(),
                                  changeListManager);

    final Collection<Change> changes = changeListManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject);
    worker.doRollback(changes, false);

    // VirtualFile instances are invalid after deletion above - find them again after rollback
    subTree.refresh(false);

    assertVF(subTree.myRootDir, "source");
    assertVF(subTree.mySourceDir, "s1.txt");
    assertVF(subTree.myRootDir, "target");

    assertVF(subTree.myTargetDir, "t11.txt");
    assertVF(subTree.myTargetDir, "t13.txt");
    assertVF(subTree.myTargetDir, "t14.txt");
    assertVF(subTree.myTargetDir, "t15.txt");

    changeListManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VirtualFile.EMPTY_ARRAY, changeListManager.getDefaultListName(),
                                                                changeListManager);
  }
  
  @Nullable
  private static VirtualFile assertVF(final VirtualFile parent, final String name) {
    final VirtualFile[] files = parent.getChildren();
    //final StringBuilder sb = new StringBuilder("Files: ");
    for (VirtualFile file : files) {
      //sb.append(file.getName()).append(' ');
      if (name.equals(file.getName())) return file;
    }
    System.out.println("not found as child");
    Assert.assertNotNull(LocalFileSystem.getInstance().findFileByIoFile(new File(parent.getPath(), name)));
    //Assert.assertTrue(sb.toString(), false);
    return null;
  }
}
