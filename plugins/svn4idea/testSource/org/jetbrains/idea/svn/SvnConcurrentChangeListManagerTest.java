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

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.DuringChangeListManagerUpdateTestScheme;
import junit.framework.Assert;
import org.junit.Test;

import java.util.Collection;

public class SvnConcurrentChangeListManagerTest extends Svn17TestCase {
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
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

    final String newName = "renamed";

    myScheme.doTest(() -> {
      changeListManager.editName(list.getName(), newName);
      checkFilesAreInList(new VirtualFile[] {file}, newName, changeListManager);
    });

    checkFilesAreInList(new VirtualFile[] {file}, newName, changeListManager);

    changeListManager.ensureUpToDate(false);
    checkFilesAreInList(new VirtualFile[] {file}, newName, changeListManager);
  }

  @Test
  public void testSwitchedFileAndFolder() throws Exception {
    final String branchUrl = prepareBranchesStructure();

    final SubTree tree = new SubTree(myWorkingCopyDir);

    runInAndVerifyIgnoreOutput("switch", branchUrl + "/root/source/s1.txt", tree.myS1File.getPath());
    runInAndVerifyIgnoreOutput("switch", branchUrl + "/root/target", tree.myTargetDir.getPath());

    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    final Runnable check = () -> {
      Assert.assertEquals(FileStatus.SWITCHED, changeListManager.getStatus(tree.myS1File));
      Assert.assertEquals(FileStatus.NOT_CHANGED, changeListManager.getStatus(tree.myS2File));
      Assert.assertEquals(FileStatus.NOT_CHANGED, changeListManager.getStatus(tree.mySourceDir));
      Assert.assertEquals(FileStatus.SWITCHED, changeListManager.getStatus(tree.myTargetDir));
      Assert.assertEquals(FileStatus.SWITCHED, changeListManager.getStatus(tree.myTargetFiles.get(1)));
    };
    // do before refresh check
    check.run();
    myScheme.doTest(check);

    changeListManager.ensureUpToDate(false);
    check.run();

    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1234543534543 3543 ");
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);

    final Runnable check2 = () -> {
      Assert.assertEquals(FileStatus.MODIFIED, changeListManager.getStatus(tree.myS1File));
      Assert.assertEquals(FileStatus.NOT_CHANGED, changeListManager.getStatus(tree.myS2File));
      Assert.assertEquals(FileStatus.NOT_CHANGED, changeListManager.getStatus(tree.mySourceDir));
      Assert.assertEquals(FileStatus.SWITCHED, changeListManager.getStatus(tree.myTargetDir));
      Assert.assertEquals(FileStatus.SWITCHED, changeListManager.getStatus(tree.myTargetFiles.get(1)));
    };
    myScheme.doTest(check2);

    changeListManager.ensureUpToDate(false);
    check2.run();
  }

  @Test
  public void testEditComment() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final String listName = "test";
    final LocalChangeList list = changeListManager.addChangeList(listName, null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

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

    changeListManager.ensureUpToDate(false);
    changedList = changeListManager.findChangeList(listName);
    assert finalText.equals(changedList.getComment());
  }

  @Test
  public void testMove() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    final LocalChangeList target = changeListManager.addChangeList("target", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

    myScheme.doTest(() -> {
      changeListManager.moveChangesTo(target, new Change[] {changeListManager.getChange(file)});
      checkFilesAreInList(new VirtualFile[] {file}, target.getName(), changeListManager);
    });

    checkFilesAreInList(new VirtualFile[] {file}, target.getName(), changeListManager);

    changeListManager.ensureUpToDate(false);
    checkFilesAreInList(new VirtualFile[] {file}, target.getName(), changeListManager);
  }

  @Test
  public void testSetActive() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    final LocalChangeList target = changeListManager.addChangeList("target", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

    myScheme.doTest(() -> {
      changeListManager.setDefaultChangeList(target);
      assert changeListManager.getDefaultChangeList().getName().equals(target.getName());
    });

    assert changeListManager.getDefaultChangeList().getName().equals(target.getName());

    changeListManager.ensureUpToDate(false);
    assert changeListManager.getDefaultChangeList().getName().equals(target.getName());
  }

  @Test
  public void testRemove() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final VirtualFile fileB = createFileInCommand("b.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

    myScheme.doTest(() -> {
      changeListManager.removeChangeList(list.getName());
      assert changeListManager.findChangeList(list.getName()) == null;
      checkFilesAreInList(new VirtualFile[] {file, fileB}, myDefaulListName, changeListManager);
    });

    assert changeListManager.findChangeList(list.getName()) == null;
    checkFilesAreInList(new VirtualFile[] {file, fileB}, myDefaulListName, changeListManager);

    changeListManager.ensureUpToDate(false);
    assert changeListManager.findChangeList(list.getName()) == null;
    checkFilesAreInList(new VirtualFile[] {file, fileB}, myDefaulListName, changeListManager);
  }

  @Test
  public void testDoubleMove() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    final LocalChangeList target = changeListManager.addChangeList("target", null);
    final LocalChangeList target2 = changeListManager.addChangeList("target2", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

    myScheme.doTest(() -> {
      changeListManager.moveChangesTo(target, new Change[] {changeListManager.getChange(file)});
      checkFilesAreInList(new VirtualFile[] {file}, target.getName(), changeListManager);
      changeListManager.moveChangesTo(target2, new Change[] {changeListManager.getChange(file)});
      checkFilesAreInList(new VirtualFile[] {file}, target2.getName(), changeListManager);
    });

    checkFilesAreInList(new VirtualFile[] {file}, target2.getName(), changeListManager);

    changeListManager.ensureUpToDate(false);
    checkFilesAreInList(new VirtualFile[] {file}, target2.getName(), changeListManager);
  }

  @Test
  public void testDoubleMoveBack() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    final LocalChangeList target = changeListManager.addChangeList("target", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

    myScheme.doTest(() -> {
      changeListManager.moveChangesTo(target, new Change[] {changeListManager.getChange(file)});
      checkFilesAreInList(new VirtualFile[] {file}, target.getName(), changeListManager);
      changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});
      checkFilesAreInList(new VirtualFile[] {file}, list.getName(), changeListManager);
    });

    checkFilesAreInList(new VirtualFile[] {file}, list.getName(), changeListManager);

    changeListManager.ensureUpToDate(false);
    checkFilesAreInList(new VirtualFile[] {file}, list.getName(), changeListManager);
  }

  @Test
  public void testAddPlusMove() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file)});

    final String targetName = "target";

    myScheme.doTest(() -> {
      final LocalChangeList target = changeListManager.addChangeList(targetName, null);
      changeListManager.moveChangesTo(target, new Change[] {changeListManager.getChange(file)});
      checkFilesAreInList(new VirtualFile[] {file}, targetName, changeListManager);
    });

    checkFilesAreInList(new VirtualFile[] {file}, targetName, changeListManager);

    changeListManager.ensureUpToDate(false);
    checkFilesAreInList(new VirtualFile[] {file}, targetName, changeListManager);
  }

  @Test
  public void testAddListBySvn() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);

    final String targetName = "target";
    // not parralel, just test of correct detection
    runInAndVerifyIgnoreOutput("changelist", targetName, file.getPath());

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);
    checkFilesAreInList(new VirtualFile[] {file}, targetName, changeListManager);
  }

  @Test
  public void testComplex() {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    final VirtualFile fileB = createFileInCommand("b.txt", "old content");
    final VirtualFile fileC = createFileInCommand("c.txt", "old content");
    final VirtualFile fileD = createFileInCommand("d.txt", "old content");

    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);

    final LocalChangeList list = changeListManager.addChangeList("test", null);
    final LocalChangeList toBeDeletedList = changeListManager.addChangeList("toBeDeletedList", null);
    changeListManager.moveChangesTo(list, new Change[] {changeListManager.getChange(file), changeListManager.getChange(fileB)});
    changeListManager.moveChangesTo(toBeDeletedList, new Change[] {changeListManager.getChange(fileC), changeListManager.getChange(fileD)});
    changeListManager.ensureUpToDate(false);

    final String targetName = "target";
    final String finalName = "final list name";

    myScheme.doTest(() -> {
      final LocalChangeList target = changeListManager.addChangeList(targetName, null);
      changeListManager.moveChangesTo(target, new Change[] {changeListManager.getChange(file), changeListManager.getChange(fileB)});
      checkFilesAreInList(new VirtualFile[] {file, fileB}, targetName, changeListManager);
      changeListManager.editName(targetName, finalName);
      checkFilesAreInList(new VirtualFile[] {file, fileB}, finalName, changeListManager);
      changeListManager.removeChangeList(toBeDeletedList.getName());
      checkFilesAreInList(new VirtualFile[] {fileC, fileD}, myDefaulListName, changeListManager);
      changeListManager.moveChangesTo(LocalChangeList.createEmptyChangeList(myProject, finalName),
                                      new Change[] {changeListManager.getChange(fileC)});
      checkFilesAreInList(new VirtualFile[] {file, fileB, fileC}, finalName, changeListManager);
      checkFilesAreInList(new VirtualFile[] {fileD}, myDefaulListName, changeListManager);
    });

    checkFilesAreInList(new VirtualFile[] {file, fileB, fileC}, finalName, changeListManager);
    checkFilesAreInList(new VirtualFile[] {fileD}, myDefaulListName, changeListManager);

    changeListManager.ensureUpToDate(false);
    checkFilesAreInList(new VirtualFile[] {file, fileB, fileC}, finalName, changeListManager);
    checkFilesAreInList(new VirtualFile[] {fileD}, myDefaulListName, changeListManager);
  }

  private void checkFilesAreInList(final VirtualFile[] files, final String listName, final ChangeListManager manager) {
    System.out.println("Checking files for list: " + listName);
    Assert.assertNotNull(manager.findChangeList(listName));
    final Collection<Change> changes = manager.findChangeList(listName).getChanges();
    Assert.assertEquals(changes.size(), files.length);

    for (Change change : changes) {
      final VirtualFile vf = change.getAfterRevision().getFile().getVirtualFile();
      boolean found = false;
      for (VirtualFile file : files) {
        if (file.equals(vf)) {
          found = true;
          break;
        }
      }
      assert found == true;
    }
  }
}
