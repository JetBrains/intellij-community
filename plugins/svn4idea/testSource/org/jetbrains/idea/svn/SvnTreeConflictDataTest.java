/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import junit.framework.Assert;
import org.jetbrains.idea.svn.conflict.ConflictAction;
import org.jetbrains.idea.svn.conflict.ConflictOperation;
import org.jetbrains.idea.svn.conflict.ConflictVersion;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

/**
 * @author Irina.Chernushina
 * @since 2.05.2012
 */
public class SvnTreeConflictDataTest extends Svn17TestCase {
  private VirtualFile myTheirs;
  private SvnClientRunnerImpl mySvnClientRunner;

  @Override
  @Before
  public void setUp() throws Exception {
    myWcRootName = "wcRootConflictData";
    myTraceClient = true;
    super.setUp();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    myTheirs = myTempDirFixture.findOrCreateDir("theirs");
    mySvnClientRunner = new SvnClientRunnerImpl(myRunner);
    mySvnClientRunner.checkout(myRepoUrl, myTheirs);
  }

  @Test
  public void testFile2File_MINE_UNV_THEIRS_ADD() throws Exception {
    final ConflictCreator creator = new ConflictCreator(myProject, myTheirs, myWorkingCopyDir,
                                                        TreeConflictData.FileToFile.MINE_UNV_THEIRS_ADD, mySvnClientRunner);
    creator.create();
    final String conflictFile = TreeConflictData.FileToFile.MINE_UNV_THEIRS_ADD.getConflictFile();

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    Assert.assertNull(beforeDescription.getSourceLeftVersion());

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isFile());
  }

  @Test
  public void testFile2File_MINE_EDIT_THEIRS_DELETE() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.FileToFile.MINE_EDIT_THEIRS_DELETE);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.DELETE, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNotNull(leftVersion);
    Assert.assertTrue(leftVersion.isFile());

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isNone());
  }

  private String createConflict(final TreeConflictData.Data data) throws Exception {
    mySvnClientRunner.testSvnVersion(myWorkingCopyDir);
    createSubTree();

    final ConflictCreator creator = new ConflictCreator(myProject, myTheirs, myWorkingCopyDir, data, mySvnClientRunner);
    creator.create();
    return data.getConflictFile();
  }

  @Test
  public void testFile2File_MINE_DELETE_THEIRS_EDIT() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.FileToFile.MINE_DELETE_THEIRS_EDIT);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.EDIT, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNotNull(leftVersion);
    Assert.assertTrue(leftVersion.isFile());

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isFile());
  }

  @Test
  public void testFile2File_MINE_EDIT_THEIRS_MOVE() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.FileToFile.MINE_EDIT_THEIRS_MOVE);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.DELETE, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNotNull(leftVersion);
    Assert.assertTrue(leftVersion.isFile());

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isNone());
  }

  @Test
  public void testFile2File_MINE_UNV_THEIRS_MOVE() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.FileToFile.MINE_UNV_THEIRS_MOVE);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNull(leftVersion);

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isFile());

  }

  @Test
  public void testFile2File_MINE_MOVE_THEIRS_EDIT() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.FileToFile.MINE_MOVE_THEIRS_EDIT);

    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.EDIT, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNotNull(leftVersion);
    Assert.assertTrue(leftVersion.isFile());

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isFile());

  }

  @Test
  public void testFile2File_MINE_MOVE_THEIRS_ADD() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.FileToFile.MINE_MOVE_THEIRS_ADD);

    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNull(leftVersion);
    //Assert.assertEquals(NodeKind.FILE, leftVersion.getKind());

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isFile());

  }

  //---------------------------------- dirs --------------------------------------------------------
  @Test
  public void testDir2Dir_MINE_UNV_THEIRS_ADD() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.DirToDir.MINE_UNV_THEIRS_ADD);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNull(leftVersion);

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isDirectory());
  }

  // not a conflict in Subversion 1.7.7. "mine" file becomes added
  /*@Test
  public void testDir2Dir_MINE_EDIT_THEIRS_DELETE() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.DirToDir.MINE_EDIT_THEIRS_DELETE);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.DELETE, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNotNull(leftVersion);
    Assert.assertEquals(NodeKind.DIR, leftVersion.getKind());

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertEquals(NodeKind.NONE, version.getKind());
  }*/

  @Test
  public void testDir2Dir_MINE_DELETE_THEIRS_EDIT() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.DirToDir.MINE_DELETE_THEIRS_EDIT);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final Change change = changeListManager.getChange(VcsUtil.getFilePath(new File(myWorkingCopyDir.getPath(), conflictFile), true));
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.EDIT, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNotNull(leftVersion);
    Assert.assertTrue(leftVersion.isDirectory());

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isDirectory());
  }

  @Test
  public void testDir2Dir_MINE_EDIT_THEIRS_MOVE() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.DirToDir.MINE_EDIT_THEIRS_MOVE);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.DELETE, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNotNull(leftVersion);
    Assert.assertTrue(leftVersion.isDirectory());

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isNone());
  }

  @Test
  public void testDir2Dir_MINE_UNV_THEIRS_MOVE() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.DirToDir.MINE_UNV_THEIRS_MOVE);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNull(leftVersion);

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isDirectory());
  }

  @Test
  public void testDir2Dir_MINE_MOVE_THEIRS_EDIT() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.DirToDir.MINE_MOVE_THEIRS_EDIT);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final Change change = changeListManager.getChange(VcsUtil.getFilePath(new File(myWorkingCopyDir.getPath(), conflictFile), true));
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.EDIT, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNotNull(leftVersion);
    Assert.assertTrue(leftVersion.isDirectory());

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isDirectory());
  }

  @Test
  public void testDir2Dir_MINE_MOVE_THEIRS_ADD() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.DirToDir.MINE_MOVE_THEIRS_ADD);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final Change change = changeListManager.getChange(VcsUtil.getFilePath(new File(myWorkingCopyDir.getPath(), conflictFile), true));
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNull(leftVersion);
    //Assert.assertEquals(NodeKind.DIR, leftVersion.getKind());

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isDirectory());
  }
  //---------------------------------
  @Test
  public void testFile2Dir_MINE_UNV_THEIRS_ADD() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.FileToDir.MINE_UNV_THEIRS_ADD);

    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNull(leftVersion);

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isDirectory());
  }

  @Test
  public void testFile2Dir_MINE_ADD_THEIRS_ADD() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.FileToDir.MINE_ADD_THEIRS_ADD);

    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNull(leftVersion);

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isDirectory());
  }

  @Test
  public void testFile2Dir_MINE_UNV_THEIRS_MOVE() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.FileToDir.MINE_UNV_THEIRS_MOVE);

    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNull(leftVersion);

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isDirectory());
  }

  @Test
  public void testFile2Dir_MINE_ADD_THEIRS_MOVE() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.FileToDir.MINE_ADD_THEIRS_MOVE);

    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNull(leftVersion);

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isDirectory());
  }

  @Test
  public void testFile2Dir_MINE_MOVE_THEIRS_ADD() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.FileToDir.MINE_MOVE_THEIRS_ADD);

    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNull(leftVersion);

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isDirectory());
  }
  //******************************************
  // dir -> file (mine, theirs)
  @Test
  public void testDir2File_MINE_UNV_THEIRS_ADD() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.DirToFile.MINE_UNV_THEIRS_ADD);

    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNull(leftVersion);

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isFile());
  }

  @Test
  public void testDir2File_MINE_ADD_THEIRS_ADD() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.DirToFile.MINE_ADD_THEIRS_ADD);

    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNull(leftVersion);

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isFile());
  }

  @Test
  public void testDir2File_MINE_UNV_THEIRS_MOVE() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.DirToFile.MINE_UNV_THEIRS_MOVE);

    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNull(leftVersion);

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isFile());
  }

  @Test
  public void testDir2File_MINE_ADD_THEIRS_MOVE() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.DirToFile.MINE_ADD_THEIRS_MOVE);

    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNull(leftVersion);

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isFile());
  }

  @Test
  public void testDir2File_MINE_MOVE_THEIRS_ADD() throws Exception {
    final String conflictFile = createConflict(TreeConflictData.DirToFile.MINE_MOVE_THEIRS_ADD);

    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
    Assert.assertNotNull(vf);
    final Change change = changeListManager.getChange(vf);
    Assert.assertTrue(change instanceof ConflictedSvnChange);
    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    Assert.assertNotNull(beforeDescription);

    final TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    Assert.assertNull(afterDescription);
    Assert.assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
    Assert.assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

    Assert.assertTrue(beforeDescription.isTreeConflict());
    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNull(leftVersion);

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertTrue(version.isFile());
  }

  private void createSubTree() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    new SubTree(myWorkingCopyDir);
    mySvnClientRunner.checkin(myWorkingCopyDir);
    sleep(10);
    mySvnClientRunner.update(myTheirs);
    mySvnClientRunner.update(myWorkingCopyDir);
    sleep(10);

    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }
}
