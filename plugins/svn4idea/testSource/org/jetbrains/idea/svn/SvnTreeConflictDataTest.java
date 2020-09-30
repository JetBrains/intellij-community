// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.conflict.ConflictAction;
import org.jetbrains.idea.svn.conflict.ConflictOperation;
import org.jetbrains.idea.svn.conflict.ConflictVersion;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.function.BiConsumer;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.junit.Assert.*;

/**
 * @author Irina.Chernushina
 */
public class SvnTreeConflictDataTest extends SvnTestCase {
  private VirtualFile myTheirs;
  private SvnClientRunnerImpl mySvnClientRunner;

  @Override
  @Before
  public void before() throws Exception {
    myWcRootName = "wcRootConflictData";
    myTraceClient = true;
    super.before();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    myTheirs = myTempDirFixture.findOrCreateDir("theirs");
    mySvnClientRunner = new SvnClientRunnerImpl(myRunner);
    mySvnClientRunner.checkout(myRepoUrl, myTheirs);
  }

  @Test
  public void testFile2File_MINE_UNV_THEIRS_ADD() throws Exception {
    assertConflict(TreeConflictData.FileToFile.MINE_UNV_THEIRS_ADD, false, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      assertNull(beforeDescription.getSourceLeftVersion());

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isFile());
    });
  }

  @Test
  public void testFile2File_MINE_EDIT_THEIRS_DELETE() throws Exception {
    assertConflict(TreeConflictData.FileToFile.MINE_EDIT_THEIRS_DELETE, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.DELETE, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNotNull(leftVersion);
      assertTrue(leftVersion.isFile());

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isNone());
    });
  }

  private String createConflict(@NotNull TreeConflictData.Data data, boolean createSubtree) throws Exception {
    if (createSubtree) {
      mySvnClientRunner.testSvnVersion(myWorkingCopyDir);
      createSubTree();
    }
    runInEdtAndWait(() -> new ConflictCreator(vcs, myTheirs, myWorkingCopyDir, data, mySvnClientRunner).create());
    return data.getConflictFile();
  }

  @Test
  public void testFile2File_MINE_DELETE_THEIRS_EDIT() throws Exception {
    assertConflict(TreeConflictData.FileToFile.MINE_DELETE_THEIRS_EDIT, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.EDIT, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNotNull(leftVersion);
      assertTrue(leftVersion.isFile());

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isFile());
    });
  }

  @Test
  public void testFile2File_MINE_EDIT_THEIRS_MOVE() throws Exception {
    assertConflict(TreeConflictData.FileToFile.MINE_EDIT_THEIRS_MOVE, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.DELETE, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNotNull(leftVersion);
      assertTrue(leftVersion.isFile());

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isNone());
    });
  }

  @Test
  public void testFile2File_MINE_UNV_THEIRS_MOVE() throws Exception {
    assertConflict(TreeConflictData.FileToFile.MINE_UNV_THEIRS_MOVE, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNull(leftVersion);

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isFile());
    });
  }

  @Test
  public void testFile2File_MINE_MOVE_THEIRS_EDIT() throws Exception {
    assertConflict(TreeConflictData.FileToFile.MINE_MOVE_THEIRS_EDIT, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.EDIT, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNotNull(leftVersion);
      assertTrue(leftVersion.isFile());

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isFile());
    });
  }

  @Test
  public void testFile2File_MINE_MOVE_THEIRS_ADD() throws Exception {
    assertConflict(TreeConflictData.FileToFile.MINE_MOVE_THEIRS_ADD, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNull(leftVersion);
      //Assert.assertEquals(NodeKind.FILE, leftVersion.getKind());

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isFile());
    });
  }

  //---------------------------------- dirs --------------------------------------------------------
  @Test
  public void testDir2Dir_MINE_UNV_THEIRS_ADD() throws Exception {
    assertConflict(TreeConflictData.DirToDir.MINE_UNV_THEIRS_ADD, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNull(leftVersion);

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isDirectory());
    });
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

    ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
    Assert.assertNotNull(leftVersion);
    Assert.assertEquals(NodeKind.DIR, leftVersion.getKind());

    final ConflictVersion version = beforeDescription.getSourceRightVersion();
    Assert.assertNotNull(version);
    Assert.assertEquals(NodeKind.NONE, version.getKind());
  }*/

  @Test
  public void testDir2Dir_MINE_DELETE_THEIRS_EDIT() throws Exception {
    assertConflict(TreeConflictData.DirToDir.MINE_DELETE_THEIRS_EDIT, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.EDIT, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNotNull(leftVersion);
      assertTrue(leftVersion.isDirectory());

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isDirectory());
    });
  }

  @Test
  public void testDir2Dir_MINE_EDIT_THEIRS_MOVE() throws Exception {
    assertConflict(TreeConflictData.DirToDir.MINE_EDIT_THEIRS_MOVE, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.DELETE, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNotNull(leftVersion);
      assertTrue(leftVersion.isDirectory());

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isNone());
    });
  }

  @Test
  public void testDir2Dir_MINE_UNV_THEIRS_MOVE() throws Exception {
    assertConflict(TreeConflictData.DirToDir.MINE_UNV_THEIRS_MOVE, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNull(leftVersion);

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isDirectory());
    });
  }

  @Test
  public void testDir2Dir_MINE_MOVE_THEIRS_EDIT() throws Exception {
    assertConflict(TreeConflictData.DirToDir.MINE_MOVE_THEIRS_EDIT, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.EDIT, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNotNull(leftVersion);
      assertTrue(leftVersion.isDirectory());

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isDirectory());
    });
  }

  @Test
  public void testDir2Dir_MINE_MOVE_THEIRS_ADD() throws Exception {
    assertConflict(TreeConflictData.DirToDir.MINE_MOVE_THEIRS_ADD, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNull(leftVersion);
      //Assert.assertEquals(NodeKind.DIR, leftVersion.getKind());

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isDirectory());
    });
  }
  //---------------------------------
  @Test
  public void testFile2Dir_MINE_UNV_THEIRS_ADD() throws Exception {
    assertConflict(TreeConflictData.FileToDir.MINE_UNV_THEIRS_ADD, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNull(leftVersion);

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isDirectory());
    });
  }

  @Test
  public void testFile2Dir_MINE_ADD_THEIRS_ADD() throws Exception {
    assertConflict(TreeConflictData.FileToDir.MINE_ADD_THEIRS_ADD, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNull(leftVersion);

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isDirectory());
    });
  }

  @Test
  public void testFile2Dir_MINE_UNV_THEIRS_MOVE() throws Exception {
    assertConflict(TreeConflictData.FileToDir.MINE_UNV_THEIRS_MOVE, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNull(leftVersion);

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isDirectory());
    });
  }

  @Test
  public void testFile2Dir_MINE_ADD_THEIRS_MOVE() throws Exception {
    assertConflict(TreeConflictData.FileToDir.MINE_ADD_THEIRS_MOVE, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNull(leftVersion);

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isDirectory());
    });
  }

  @Test
  public void testFile2Dir_MINE_MOVE_THEIRS_ADD() throws Exception {
    assertConflict(TreeConflictData.FileToDir.MINE_MOVE_THEIRS_ADD, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNull(leftVersion);

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isDirectory());
    });
  }
  //******************************************
  // dir -> file (mine, theirs)
  @Test
  public void testDir2File_MINE_UNV_THEIRS_ADD() throws Exception {
    assertConflict(TreeConflictData.DirToFile.MINE_UNV_THEIRS_ADD, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNull(leftVersion);

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isFile());
    });
  }

  @Test
  public void testDir2File_MINE_ADD_THEIRS_ADD() throws Exception {
    assertConflict(TreeConflictData.DirToFile.MINE_ADD_THEIRS_ADD, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNull(leftVersion);

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isFile());
    });
  }

  @Test
  public void testDir2File_MINE_UNV_THEIRS_MOVE() throws Exception {
    assertConflict(TreeConflictData.DirToFile.MINE_UNV_THEIRS_MOVE, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNull(leftVersion);

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isFile());
    });
  }

  @Test
  public void testDir2File_MINE_ADD_THEIRS_MOVE() throws Exception {
    assertConflict(TreeConflictData.DirToFile.MINE_ADD_THEIRS_MOVE, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNull(leftVersion);

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isFile());
    });
  }

  @Test
  public void testDir2File_MINE_MOVE_THEIRS_ADD() throws Exception {
    assertConflict(TreeConflictData.DirToFile.MINE_MOVE_THEIRS_ADD, (beforeDescription, afterDescription) -> {
      assertEquals(ConflictOperation.UPDATE, beforeDescription.getOperation());
      assertEquals(ConflictAction.ADD, beforeDescription.getConflictAction());

      ConflictVersion leftVersion = beforeDescription.getSourceLeftVersion();
      assertNull(leftVersion);

      final ConflictVersion version = beforeDescription.getSourceRightVersion();
      assertNotNull(version);
      assertTrue(version.isFile());
    });
  }

  private void createSubTree() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    new SubTree(myWorkingCopyDir);
    mySvnClientRunner.checkin(myWorkingCopyDir);
    mySvnClientRunner.update(myTheirs);
    mySvnClientRunner.update(myWorkingCopyDir);

    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  private void assertConflict(@NotNull TreeConflictData.Data data,
                              @NotNull BiConsumer<TreeConflictDescription, TreeConflictDescription> checker) throws Exception {
    assertConflict(data, true, checker);
  }

  private void assertConflict(@NotNull TreeConflictData.Data data,
                              boolean createSubtree,
                              @NotNull BiConsumer<TreeConflictDescription, TreeConflictDescription> checker) throws Exception {
    String conflictFile = createConflict(data, createSubtree);

    refreshChanges();

    Change change;
    if (data == TreeConflictData.DirToDir.MINE_DELETE_THEIRS_EDIT ||
        data == TreeConflictData.DirToDir.MINE_MOVE_THEIRS_EDIT ||
        data == TreeConflictData.DirToDir.MINE_MOVE_THEIRS_ADD) {
      change = changeListManager.getChange(VcsUtil.getFilePath(new File(myWorkingCopyDir.getPath(), conflictFile), true));
    }
    else {
      VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myWorkingCopyDir.getPath(), conflictFile));
      assertNotNull(vf);

      change = changeListManager.getChange(vf);
    }

    assertTrue(change instanceof ConflictedSvnChange);

    TreeConflictDescription beforeDescription = ((ConflictedSvnChange)change).getBeforeDescription();
    TreeConflictDescription afterDescription = ((ConflictedSvnChange)change).getAfterDescription();
    assertNotNull(beforeDescription);
    assertNull(afterDescription);

    checker.accept(beforeDescription, afterDescription);
  }
}
