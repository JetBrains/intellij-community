/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.ui.RollbackWorker;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.DuringChangeListManagerUpdateTestScheme;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.SvnTestCase;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/4/13
 * Time: 2:07 PM
 */
public class SvnChangesCorrectlyRefreshedNativeTest extends Svn17TestCase {
  private ChangeListManager clManager;

  @Override
  public void setUp() throws Exception {
    setUseNativeAcceleration(true);
    super.setUp();

    clManager = ChangeListManager.getInstance(myProject);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  @Test
  public void testModificationAndAfterRevert() throws Exception {
    final SvnTestCase.SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();
    sleep(100);

    VcsTestUtil.editFileInCommand(myProject, subTree.myS1File, "new content");

    final CharSequence text1 = LoadTextUtil.loadText(subTree.myS1File);
    Assert.assertEquals("new content", text1.toString());

    sleep(100);
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(subTree.myS1File.getPath()));
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    final VcsException updateException = ((ChangeListManagerImpl)clManager).getUpdateException();
    if (updateException != null) {
      updateException.printStackTrace();
    }
    Assert.assertNull(updateException == null ? null : updateException.getMessage(), updateException);

    DuringChangeListManagerUpdateTestScheme
      .checkFilesAreInList(new VirtualFile[]{subTree.myS1File}, clManager.getDefaultListName(), clManager);

    final Collection<Change> changes = clManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject);
    worker.doRollback(changes, false, null, null);

    final CharSequence text = LoadTextUtil.loadText(subTree.myS1File);
    Assert.assertEquals(SubTree.ourS1Contents, text.toString());

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VirtualFile.EMPTY_ARRAY, clManager.getDefaultListName(), clManager);
  }

  @Test
  public void testRenameFileAndAfterRevert() throws Throwable {
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();

    final String newName = "newName";
    renameFileInCommand(subTree.myS1File, newName);

    assertVF(subTree.mySourceDir, newName);

    sleep(300);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.myS1File}, clManager.getDefaultListName(), clManager);

    final Collection<Change> changes = clManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject);
    worker.doRollback(changes, false, null, null);

    assertVF(subTree.mySourceDir, "s1.txt");

    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VirtualFile.EMPTY_ARRAY, clManager.getDefaultListName(), clManager);
  }

  @Test
  public void testMoveFileAndAfterRevert() throws Throwable {
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();

    moveFileInCommand(subTree.myS1File, subTree.myTargetDir);

    assertVF(subTree.myTargetDir, "s1.txt");

    sleep(300);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.myS1File}, clManager.getDefaultListName(), clManager);

    final Collection<Change> changes = clManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject);
    worker.doRollback(changes, false, null, null);

    assertVF(subTree.mySourceDir, "s1.txt");

    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VirtualFile.EMPTY_ARRAY, clManager.getDefaultListName(), clManager);
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
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.mySourceDir, subTree.myS1File, subTree.myS2File},
                                                                clManager.getDefaultListName(), clManager);

    final Collection<Change> changes = clManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject);
    worker.doRollback(changes, false, null, null);

    subTree.mySourceDir = assertVF(subTree.myRootDir, "source");
    Assert.assertTrue(subTree.mySourceDir.getPath().endsWith("/root/source"));
    assertVF(subTree.mySourceDir, "s1.txt");
    assertVF(subTree.mySourceDir, "s2.txt");

    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VirtualFile.EMPTY_ARRAY, clManager.getDefaultListName(), clManager);
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
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(new VirtualFile[] {subTree.mySourceDir, subTree.myS1File, subTree.myS2File},
                                                                clManager.getDefaultListName(), clManager);

    final Collection<Change> changes = clManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject);
    worker.doRollback(changes, false, null, null);

    subTree.mySourceDir = assertVF(subTree.myRootDir, "source");
    Assert.assertTrue(subTree.mySourceDir.getPath().endsWith("/root/source"));

    subTree.myS1File = assertVF(subTree.mySourceDir, "s1.txt");
    subTree.myS2File = assertVF(subTree.mySourceDir, "s2.txt");
    final CharSequence text = LoadTextUtil.loadText(subTree.myS1File);
    Assert.assertEquals(SubTree.ourS1Contents, text.toString());

    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VirtualFile.EMPTY_ARRAY, clManager.getDefaultListName(), clManager);
  }

  @Test
  public void testAddDirEditFileAndAfterRevert() throws Throwable {
    final SubTree subTree = new SubTree(myWorkingCopyDir);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    final List<VirtualFile> files = getAllFiles(subTree);

    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VfsUtil.toVirtualFileArray(files), clManager.getDefaultListName(), clManager);

    final Collection<Change> changes = clManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject);
    worker.doRollback(changes, false, null, null);

    assertVF(subTree.myRootDir, "source");
    assertVF(subTree.mySourceDir, "s1.txt");
    assertVF(subTree.myRootDir, "target");

    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VirtualFile.EMPTY_ARRAY, clManager.getDefaultListName(), clManager);

    for (VirtualFile file : files) {
      Assert.assertTrue(file.getPath(), clManager.isUnversioned(file));
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

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    clManager.ensureUpToDate(false);
    final List<VirtualFile> files = getAllFiles(subTree);
    DuringChangeListManagerUpdateTestScheme.checkDeletedFilesAreInList(VfsUtil.toVirtualFileArray(files), clManager.getDefaultListName(),
                                                                       clManager);

    final Collection<Change> changes = clManager.getDefaultChangeList().getChanges();

    final RollbackWorker worker = new RollbackWorker(myProject);
    worker.doRollback(changes, false, null, null);

    assertVF(subTree.myRootDir, "source");
    assertVF(subTree.mySourceDir, "s1.txt");
    assertVF(subTree.myRootDir, "target");

    assertVF(subTree.myTargetDir, "t11.txt");
    assertVF(subTree.myTargetDir, "t13.txt");
    assertVF(subTree.myTargetDir, "t14.txt");
    assertVF(subTree.myTargetDir, "t15.txt");

    clManager.ensureUpToDate(false);
    DuringChangeListManagerUpdateTestScheme.checkFilesAreInList(VirtualFile.EMPTY_ARRAY, clManager.getDefaultListName(), clManager);
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
