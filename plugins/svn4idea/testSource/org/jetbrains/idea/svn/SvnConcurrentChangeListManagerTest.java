// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.DuringChangeListManagerUpdateTestScheme;
import org.junit.Test;

import java.util.Collection;

import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.*;

public class SvnConcurrentChangeListManagerTest extends SvnTestCase {
  private DuringChangeListManagerUpdateTestScheme myScheme;
  private String myDefaulListName;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDefaulListName = LocalChangeList.DEFAULT_NAME;
    myScheme = new DuringChangeListManagerUpdateTestScheme(myProject, myTempDirFixture.getTempDirPath());
  }

  @Test
  public void testRenameList() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    final VirtualFile file = createFileInCommand("a.txt", "old content");
    changeListManager.ensureUpToDate();

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    changeListManager.moveChangesTo(list, changeListManager.getChange(file));

    final String newName = "renamed";

    myScheme.doTest(() -> {
      changeListManager.editName(list.getName(), newName);
      checkFilesAreInList(newName, file);
    });

    checkFilesAreInList(newName, file);

    changeListManager.ensureUpToDate();
    checkFilesAreInList(newName, file);
  }

  @Test
  public void testSwitchedFileAndFolder() throws Exception {
    final String branchUrl = prepareBranchesStructure();
    final SubTree tree = new SubTree(myWorkingCopyDir);

    runInAndVerifyIgnoreOutput("switch", branchUrl + "/root/source/s1.txt", tree.myS1File.getPath());
    runInAndVerifyIgnoreOutput("switch", branchUrl + "/root/target", tree.myTargetDir.getPath());

    refreshChanges();

    final Runnable check = () -> {
      assertEquals(FileStatus.SWITCHED, changeListManager.getStatus(tree.myS1File));
      assertEquals(FileStatus.NOT_CHANGED, changeListManager.getStatus(tree.myS2File));
      assertEquals(FileStatus.NOT_CHANGED, changeListManager.getStatus(tree.mySourceDir));
      assertEquals(FileStatus.SWITCHED, changeListManager.getStatus(tree.myTargetDir));
      assertEquals(FileStatus.SWITCHED, changeListManager.getStatus(tree.myTargetFiles.get(1)));
    };
    // do before refresh check
    check.run();
    myScheme.doTest(check);

    changeListManager.ensureUpToDate();
    check.run();

    editFileInCommand(tree.myS1File, "1234543534543 3543 ");
    refreshChanges();

    final Runnable check2 = () -> {
      assertEquals(FileStatus.MODIFIED, changeListManager.getStatus(tree.myS1File));
      assertEquals(FileStatus.NOT_CHANGED, changeListManager.getStatus(tree.myS2File));
      assertEquals(FileStatus.NOT_CHANGED, changeListManager.getStatus(tree.mySourceDir));
      assertEquals(FileStatus.SWITCHED, changeListManager.getStatus(tree.myTargetDir));
      assertEquals(FileStatus.SWITCHED, changeListManager.getStatus(tree.myTargetFiles.get(1)));
    };
    myScheme.doTest(check2);

    changeListManager.ensureUpToDate();
    check2.run();
  }

  @Test
  public void testEditComment() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    final VirtualFile file = createFileInCommand("a.txt", "old content");
    changeListManager.ensureUpToDate();

    final String listName = "test";
    final LocalChangeList list = changeListManager.addChangeList(listName, null);
    changeListManager.moveChangesTo(list, changeListManager.getChange(file));

    final String finalText = "final text";

    myScheme.doTest(() -> {
      final String intermediate = "intermediate text";
      changeListManager.editComment(list.getName(), intermediate);
      assert changeListManager.findChangeList(listName) != null;
      LocalChangeList list1 = changeListManager.findChangeList(listName);
      assert intermediate.equals(list1.getComment());

      changeListManager.editComment(list1.getName(), finalText);
      list1 = changeListManager.findChangeList(listName);
      assert finalText.equals(list1.getComment());
    });

    LocalChangeList changedList = changeListManager.findChangeList(listName);
    assert finalText.equals(changedList.getComment());

    changeListManager.ensureUpToDate();
    changedList = changeListManager.findChangeList(listName);
    assert finalText.equals(changedList.getComment());
  }

  @Test
  public void testMove() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    final VirtualFile file = createFileInCommand("a.txt", "old content");
    changeListManager.ensureUpToDate();

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    final LocalChangeList target = changeListManager.addChangeList("target", null);
    changeListManager.moveChangesTo(list, changeListManager.getChange(file));

    myScheme.doTest(() -> {
      changeListManager.moveChangesTo(target, changeListManager.getChange(file));
      checkFilesAreInList(target.getName(), file);
    });

    checkFilesAreInList(target.getName(), file);

    changeListManager.ensureUpToDate();
    checkFilesAreInList(target.getName(), file);
  }

  @Test
  public void testSetActive() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    final VirtualFile file = createFileInCommand("a.txt", "old content");
    changeListManager.ensureUpToDate();

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    final LocalChangeList target = changeListManager.addChangeList("target", null);
    changeListManager.moveChangesTo(list, changeListManager.getChange(file));

    myScheme.doTest(() -> {
      changeListManager.setDefaultChangeList(target);
      assert changeListManager.getDefaultChangeList().getName().equals(target.getName());
    });

    assert changeListManager.getDefaultChangeList().getName().equals(target.getName());

    changeListManager.ensureUpToDate();
    assert changeListManager.getDefaultChangeList().getName().equals(target.getName());
  }

  @Test
  public void testRemove() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final VirtualFile fileB = createFileInCommand("b.txt", "old content");
    changeListManager.ensureUpToDate();

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    changeListManager.moveChangesTo(list, changeListManager.getChange(file));

    myScheme.doTest(() -> {
      changeListManager.removeChangeList(list.getName());
      assert changeListManager.findChangeList(list.getName()) == null;
      checkFilesAreInList(myDefaulListName, file, fileB);
    });

    assert changeListManager.findChangeList(list.getName()) == null;
    checkFilesAreInList(myDefaulListName, file, fileB);

    changeListManager.ensureUpToDate();
    assert changeListManager.findChangeList(list.getName()) == null;
    checkFilesAreInList(myDefaulListName, file, fileB);
  }

  @Test
  public void testDoubleMove() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    final VirtualFile file = createFileInCommand("a.txt", "old content");
    changeListManager.ensureUpToDate();

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    final LocalChangeList target = changeListManager.addChangeList("target", null);
    final LocalChangeList target2 = changeListManager.addChangeList("target2", null);
    changeListManager.moveChangesTo(list, changeListManager.getChange(file));

    myScheme.doTest(() -> {
      changeListManager.moveChangesTo(target, changeListManager.getChange(file));
      checkFilesAreInList(target.getName(), file);
      changeListManager.moveChangesTo(target2, changeListManager.getChange(file));
      checkFilesAreInList(target2.getName(), file);
    });

    checkFilesAreInList(target2.getName(), file);

    changeListManager.ensureUpToDate();
    checkFilesAreInList(target2.getName(), file);
  }

  @Test
  public void testDoubleMoveBack() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    final VirtualFile file = createFileInCommand("a.txt", "old content");
    changeListManager.ensureUpToDate();

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    final LocalChangeList target = changeListManager.addChangeList("target", null);
    changeListManager.moveChangesTo(list, changeListManager.getChange(file));

    myScheme.doTest(() -> {
      changeListManager.moveChangesTo(target, changeListManager.getChange(file));
      checkFilesAreInList(target.getName(), file);
      changeListManager.moveChangesTo(list, changeListManager.getChange(file));
      checkFilesAreInList(list.getName(), file);
    });

    checkFilesAreInList(list.getName(), file);

    changeListManager.ensureUpToDate();
    checkFilesAreInList(list.getName(), file);
  }

  @Test
  public void testAddPlusMove() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    final VirtualFile file = createFileInCommand("a.txt", "old content");
    changeListManager.ensureUpToDate();

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    changeListManager.moveChangesTo(list, changeListManager.getChange(file));

    final String targetName = "target";

    myScheme.doTest(() -> {
      final LocalChangeList target = changeListManager.addChangeList(targetName, null);
      changeListManager.moveChangesTo(target, changeListManager.getChange(file));
      checkFilesAreInList(targetName, file);
    });

    checkFilesAreInList(targetName, file);

    changeListManager.ensureUpToDate();
    checkFilesAreInList(targetName, file);
  }

  @Test
  public void testAddListBySvn() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final String targetName = "target";
    // not parralel, just test of correct detection
    runInAndVerifyIgnoreOutput("changelist", targetName, file.getPath());

    refreshChanges();
    checkFilesAreInList(targetName, file);
  }

  @Test
  public void testComplex() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final VirtualFile fileB = createFileInCommand("b.txt", "old content");
    final VirtualFile fileC = createFileInCommand("c.txt", "old content");
    final VirtualFile fileD = createFileInCommand("d.txt", "old content");

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    final LocalChangeList toBeDeletedList = changeListManager.addChangeList("toBeDeletedList", null);
    changeListManager.moveChangesTo(list, changeListManager.getChange(file), changeListManager.getChange(fileB));
    changeListManager.moveChangesTo(toBeDeletedList, changeListManager.getChange(fileC), changeListManager.getChange(fileD));
    changeListManager.ensureUpToDate();

    final String targetName = "target";
    final String finalName = "final list name";

    myScheme.doTest(() -> {
      final LocalChangeList target = changeListManager.addChangeList(targetName, null);
      changeListManager.moveChangesTo(target, changeListManager.getChange(file), changeListManager.getChange(fileB));
      checkFilesAreInList(targetName, file, fileB);
      changeListManager.editName(targetName, finalName);
      checkFilesAreInList(finalName, file, fileB);
      changeListManager.removeChangeList(toBeDeletedList.getName());
      checkFilesAreInList(myDefaulListName, fileC, fileD);
      changeListManager.moveChangesTo(LocalChangeList.createEmptyChangeList(myProject, finalName), changeListManager.getChange(fileC));
      checkFilesAreInList(finalName, file, fileB, fileC);
      checkFilesAreInList(myDefaulListName, fileD);
    });

    checkFilesAreInList(finalName, file, fileB, fileC);
    checkFilesAreInList(myDefaulListName, fileD);

    changeListManager.ensureUpToDate();
    checkFilesAreInList(finalName, file, fileB, fileC);
    checkFilesAreInList(myDefaulListName, fileD);
  }

  private void checkFilesAreInList(final String listName, VirtualFile... files) {
    System.out.println("Checking files for list: " + listName);
    assertNotNull(changeListManager.findChangeList(listName));
    final Collection<Change> changes = changeListManager.findChangeList(listName).getChanges();
    assertEquals(changes.size(), files.length);

    for (Change change : changes) {
      assertThat(change.getAfterRevision().getFile().getVirtualFile(), isIn(files));
    }
  }
}
