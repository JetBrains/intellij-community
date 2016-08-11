/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NonNls;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author yole
 */
public class SvnRenameTest extends Svn17TestCase {
  @NonNls private static final String LOG_SEPARATOR = "------------------------------------------------------------------------\n";
  @NonNls private static final String LOG_SEPARATOR_START = "-------------";

  public SvnRenameTest() {
    myInitChangeListManager = false;
  }

  @Test
  public void testSimpleRename() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile a = createFileInCommand("a.txt", "test");
    checkin();

    renameFileInCommand(a, "b.txt");
    verifySorted(runSvn("status"), "A + b.txt", "D a.txt");
  }

  // IDEADEV-18844
  @Test
  public void testRenameReplace() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile a = createFileInCommand("a.txt", "old");
    final VirtualFile aNew = createFileInCommand("aNew.txt", "new");
    checkin();

    renameFileInCommand(a, "aOld.txt");
    renameFileInCommand(aNew, "a.txt");
    final ProcessOutput result = runSvn("status");
    verifySorted(result, "A + aOld.txt", "D aNew.txt", "R + a.txt");
  }

  // IDEADEV-16251
  @Test
  public void testRenameAddedPackage() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile dir = createDirInCommand(myWorkingCopyDir, "child");
    createFileInCommand(dir, "a.txt", "content");
    renameFileInCommand(dir, "newchild");
    verifySorted(runSvn("status"), "A newchild", "A newchild" + File.separatorChar + "a.txt");
  }

  // IDEADEV-8091
  @Test
  public void testDoubleRename() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile a = createFileInCommand("a.txt", "test");
    checkin();

    renameFileInCommand(a, "b.txt");
    renameFileInCommand(a, "c.txt");
    verifySorted(runSvn("status"), "A + c.txt", "D a.txt");
  }

  // IDEADEV-15876
  @Test
  public void testRenamePackageWithChildren() throws Exception {
    final VirtualFile child = prepareDirectoriesForRename();

    renameFileInCommand(child, "childnew");
    final ProcessOutput result = runSvn("status");
    verifySorted(result, "A + childnew",
                 "D child",
                 "D child" + File.separatorChar + "a.txt",
                 "D child" + File.separatorChar + "grandChild",
                 "D child" + File.separatorChar + "grandChild" + File.separatorChar + "b.txt");

    refreshVfs();   // wait for end of refresh operations initiated from SvnFileSystemListener
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    insideInitializedChangeListManager(changeListManager, new Runnable() {
      @Override
      public void run() {
        changeListManager.ensureUpToDate(false);
        List<Change> changes = new ArrayList<>(changeListManager.getDefaultChangeList().getChanges());
        Assert.assertEquals(4, changes.size());
        sortChanges(changes);
        verifyChange(changes.get(0), "child", "childnew");
        verifyChange(changes.get(1), "child" + File.separatorChar + "a.txt", "childnew" + File.separatorChar + "a.txt");
        verifyChange(changes.get(2), "child" + File.separatorChar + "grandChild", "childnew" + File.separatorChar + "grandChild");
        verifyChange(changes.get(3), "child" + File.separatorChar + "grandChild" + File.separatorChar + "b.txt", "childnew" + File.separatorChar + "grandChild" + File.separatorChar + "b.txt");
      }
    });

    // there is no such directory any more
    /*VirtualFile oldChild = myWorkingCopyDir.findChild("child");
    if (oldChild == null) {
      myWorkingCopyDir.refresh(false, true);
      oldChild = myWorkingCopyDir.findChild("child");
    }
    Assert.assertEquals(FileStatus.DELETED, changeListManager.getStatus(oldChild));*/
  }

  private void insideInitializedChangeListManager(final ChangeListManager changeListManager, final Runnable runnable) {
    try {
      runnable.run();
    } finally {
      ((ChangeListManagerImpl) changeListManager).stopEveryThingIfInTestMode();
    }
  }

  private VirtualFile prepareDirectoriesForRename() throws IOException {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile child = createDirInCommand(myWorkingCopyDir, "child");
    final VirtualFile grandChild = createDirInCommand(child, "grandChild");
    createFileInCommand(child, "a.txt", "a");
    createFileInCommand(grandChild, "b.txt", "b");
    checkin();
    return child;
  }

  // IDEADEV-19065
  @Test
  public void testCommitAfterRenameDir() throws Exception {
    final VirtualFile child = prepareDirectoriesForRename();

    renameFileInCommand(child, "newchild");
    checkin();

    final ProcessOutput runResult = runSvn("log", "-q", "newchild/a.txt");
    verify(runResult);
    final List<String> lines = StringUtil.split(runResult.getStdout(), "\n");
    for (Iterator<String> iterator = lines.iterator(); iterator.hasNext();) {
      final String next = iterator.next();
      if (next.startsWith(LOG_SEPARATOR_START)) {
        iterator.remove();
      }
    }
    Assert.assertEquals(2, lines.size());
    Assert.assertTrue(lines.get(0).startsWith("r2 |"));
    Assert.assertTrue(lines.get(1).startsWith("r1 |"));
  }

  // todo - undo; undo after commit
  // IDEADEV-9755
  @Test
  public void testRollbackRenameDir() throws Exception {
    final VirtualFile child = prepareDirectoriesForRename();
    renameFileInCommand(child, "newchild");

    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    insideInitializedChangeListManager(changeListManager, new Runnable() {
      @Override
      public void run() {
        changeListManager.ensureUpToDate(false);
        final Change change = changeListManager.getChange(myWorkingCopyDir.findChild("newchild"));
        Assert.assertNotNull(change);

        final List<VcsException> exceptions = new ArrayList<>();
        SvnVcs.getInstance(myProject).getRollbackEnvironment().rollbackChanges(Collections.singletonList(change), exceptions,
                                                                               RollbackProgressListener.EMPTY);
        Assert.assertTrue(exceptions.isEmpty());
        Assert.assertFalse(new File(myWorkingCopyDir.getPath(), "newchild").exists());
        Assert.assertTrue(new File(myWorkingCopyDir.getPath(), "child").exists());
      }
    });
  }

  // todo undo; undo after commit
  // IDEADEV-7697
  @Test
  public void testMovePackageToParent() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile child = createDirInCommand(myWorkingCopyDir, "child");
    final VirtualFile grandChild = createDirInCommand(child, "grandChild");
    createFileInCommand(grandChild, "a.txt", "a");
    checkin();
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    insideInitializedChangeListManager(changeListManager, new Runnable() {
      @Override
      public void run() {
        moveFileInCommand(grandChild, myWorkingCopyDir);
        refreshVfs();   // wait for end of refresh operations initiated from SvnFileSystemListener
        changeListManager.ensureUpToDate(false);
        final List<Change> changes = new ArrayList<>(changeListManager.getDefaultChangeList().getChanges());
        Assert.assertEquals(listToString(changes), 2, changes.size());
        sortChanges(changes);
        verifyChange(changes.get(0), "child" + File.separatorChar + "grandChild", "grandChild");
        verifyChange(changes.get(1), "child" + File.separatorChar + "grandChild" + File.separatorChar + "a.txt",
                     "grandChild" + File.separatorChar + "a.txt");
      }
    });
  }

  private String listToString(final List<Change> changes) {
    return "{" + StringUtil.join(changes, StringUtil.createToStringFunction(Change.class), ",") + "}";
  }

  // IDEADEV-19223
  @Test
  public void testRollbackRenameWithUnversioned() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile child = createDirInCommand(myWorkingCopyDir, "child");
    createFileInCommand(child, "a.txt", "a");
    checkin();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile unversioned = createFileInCommand(child, "u.txt", "u");
    final VirtualFile unversionedDir = createDirInCommand(child, "uc");
    createFileInCommand(unversionedDir, "c.txt", "c");

    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    insideInitializedChangeListManager(changeListManager, new Runnable() {
      @Override
      public void run() {
        changeListManager.ensureUpToDate(false);
        Assert.assertEquals(FileStatus.UNKNOWN, changeListManager.getStatus(unversioned));

        renameFileInCommand(child, "newchild");
        File childPath = new File(myWorkingCopyDir.getPath(), "child");
        File newChildPath = new File(myWorkingCopyDir.getPath(), "newchild");
        Assert.assertTrue(new File(newChildPath, "a.txt").exists());
        Assert.assertTrue(new File(newChildPath, "u.txt").exists());
        Assert.assertFalse(new File(childPath, "u.txt").exists());

        refreshVfs();
        changeListManager.ensureUpToDate(false);
        final List<Change> changes = new ArrayList<>();
        changes.add(ChangeListManager.getInstance(myProject).getChange(myWorkingCopyDir.findChild("newchild").findChild("a.txt")));
        changes.add(ChangeListManager.getInstance(myProject).getChange(myWorkingCopyDir.findChild("newchild")));

        final List<VcsException> exceptions = new ArrayList<>();
        SvnVcs.getInstance(myProject).getRollbackEnvironment().rollbackChanges(changes, exceptions, RollbackProgressListener.EMPTY);
        TimeoutUtil.sleep(300);
        Assert.assertTrue(exceptions.isEmpty());
        final File fileA = new File(childPath, "a.txt");
        Assert.assertTrue(fileA.getAbsolutePath(), fileA.exists());
        final File fileU = new File(childPath, "u.txt");
        Assert.assertTrue(fileU.getAbsolutePath(), fileU.exists());
        final File unversionedDirFile = new File(childPath, "uc");
        Assert.assertTrue(unversionedDirFile.exists());
        Assert.assertTrue(new File(unversionedDirFile, "c.txt").exists());
      }
    });
  }

  // IDEA-13824
  @Test
  public void testRenameFileRenameDir() throws Exception {
    setNativeAcceleration(true);  //todo debug
    final VirtualFile child = prepareDirectoriesForRename();
    final VirtualFile f = child.findChild("a.txt");
    renameFileInCommand(f, "anew.txt");
    renameFileInCommand(child, "newchild");

    verifySorted(runSvn("status"), "A + newchild", "A + newchild" + File.separatorChar + "anew.txt",
                 "D child", "D child" + File.separatorChar + "a.txt", "D child" + File.separatorChar + "grandChild", "D child" + File.separatorChar + "grandChild" + File.separatorChar + "b.txt", "D + newchild" + File.separatorChar + "a.txt");

    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    insideInitializedChangeListManager(changeListManager, new Runnable() {
      @Override
      public void run() {
        refreshVfs();   // wait for end of refresh operations initiated from SvnFileSystemListener
        changeListManager.ensureUpToDate(false);
        final List<Change> changes = new ArrayList<>(changeListManager.getDefaultChangeList().getChanges());
        final List<VcsException> list = SvnVcs.getInstance(myProject).getCheckinEnvironment().commit(changes, "test");
        Assert.assertEquals(0, list.size());
      }
    });
  }

  // IDEADEV-19364
  @Test
  public void testUndoMovePackage() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile parent1 = createDirInCommand(myWorkingCopyDir, "parent1");
    final VirtualFile parent2 = createDirInCommand(myWorkingCopyDir, "parent2");
    final VirtualFile child = createDirInCommand(parent1, "child");
    createFileInCommand(child, "a.txt", "a");
    checkin();

    moveFileInCommand(child, parent2);
    undo();
    final File childPath = new File(parent1.getPath(), "child");
    Assert.assertTrue(childPath.exists());
    Assert.assertTrue(new File(childPath, "a.txt").exists());
  }

  // IDEADEV-19552
  @Test
  public void testUndoRename() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "A");
    checkin();

    renameFileInCommand(file, "b.txt");
    undo();
    Assert.assertTrue(new File(myWorkingCopyDir.getPath(), "a.txt").exists());
    Assert.assertFalse(new File(myWorkingCopyDir.getPath(), "b.txt").exists());
  }

  @Test
  public void testUndoCommittedRenameFile() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "A");
    checkin();

    renameFileInCommand(file, "b.txt");
    checkin();
    undo();
    verifySorted(runSvn("status"), "A + a.txt", "D b.txt");
  }

  // IDEADEV-19336
  @Test
  public void testUndoMoveCommittedPackage() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VirtualFile parent1 = createDirInCommand(myWorkingCopyDir, "parent1");
    final VirtualFile parent2 = createDirInCommand(myWorkingCopyDir, "parent2");
    final VirtualFile child = createDirInCommand(parent1, "child");
    createFileInCommand(child, "a.txt", "a");
    checkin();

    moveFileInCommand(child, parent2);
    checkin();

    undo();
    verifySorted(runSvn("status"), "A + parent1" + File.separatorChar + "child",
                 "D parent2" + File.separatorChar + "child",
                 "D parent2" + File.separatorChar + "child" + File.separatorChar + "a.txt");
  }

  @Test
  public void testMoveToUnversioned() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "A");
    final VirtualFile child = moveToNewPackage(file, "child");
    verifySorted(runSvn("status"), "A child", "A child" + File.separatorChar + "a.txt");
    checkin();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile unversioned = createDirInCommand(myWorkingCopyDir, "unversioned");
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    verifySorted(runSvn("status"), "? unversioned");

    moveFileInCommand(child, unversioned);
    verifySorted(runSvn("status"), "? unversioned", "D child", "D child" + File.separator + "a.txt");
  }

  @Test
  public void testUndoMoveToUnversioned() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "A");
    final VirtualFile child = moveToNewPackage(file, "child");
    verifySorted(runSvn("status"), "A child", "A child" + File.separatorChar + "a.txt");
    checkin();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile unversioned = createDirInCommand(myWorkingCopyDir, "unversioned");
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    verifySorted(runSvn("status"), "? unversioned");

    moveFileInCommand(child, unversioned);
    verifySorted(runSvn("status"), "? unversioned", "D child", "D child" + File.separator + "a.txt");

    undo();
    verifySorted(runSvn("status"), "? unversioned");
  }

  @Test
  public void testUndoMoveUnversionedToUnversioned() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "A");
    verifySorted(runSvn("status"), "? a.txt");
    final VirtualFile unversioned = createDirInCommand(myWorkingCopyDir, "unversioned");
    moveFileInCommand(file, unversioned);
    verifySorted(runSvn("status"), "? unversioned");
    undo();
    verifySorted(runSvn("status"), "? a.txt", "? unversioned");
  }

  @Test
  public void testUndoMoveAddedToUnversioned() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "A");
    verifySorted(runSvn("status"), "A a.txt");
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile unversioned = createDirInCommand(myWorkingCopyDir, "unversioned");
    moveFileInCommand(file, unversioned);
    verifySorted(runSvn("status"), "? unversioned");
    undo();
    verifySorted(runSvn("status"), "? a.txt", "? unversioned");
  }

  @Test
  public void testUndoMoveToUnversionedCommitted() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "A");
    final VirtualFile child = moveToNewPackage(file, "child");
    verifySorted(runSvn("status"), "A child", "A child" + File.separatorChar + "a.txt");
    checkin();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile unversioned = createDirInCommand(myWorkingCopyDir, "unversioned");
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    verifySorted(runSvn("status"), "? unversioned");

    moveFileInCommand(child, unversioned);
    verifySorted(runSvn("status"), "? unversioned", "D child", "D child" + File.separator + "a.txt");
    checkin();

    undo();
    verifySorted(runSvn("status"), "? child", "? unversioned");
  }

  // IDEA-92941
  @Test
  public void testUndoNewMove() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile sink = createDirInCommand(myWorkingCopyDir, "sink");
    final VirtualFile child = createDirInCommand(myWorkingCopyDir, "child");
    verifySorted(runSvn("status"), "A child", "A sink");
    checkin();
    final VirtualFile file = createFileInCommand(child, "a.txt", "A");
    verifySorted(runSvn("status"), "A child" + File.separatorChar + "a.txt");
    moveFileInCommand(file, sink);
    verifySorted(runSvn("status"), "A sink" + File.separatorChar + "a.txt");
    undo();
    verifySorted(runSvn("status"), "A child" + File.separatorChar + "a.txt");
  }

  // todo undo, undo committed?
  @Test
  public void testMoveToNewPackage() throws Throwable {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "A");
    moveToNewPackage(file, "child");
    verifySorted(runSvn("status"), "A child", "A child" + File.separatorChar + "a.txt");
  }

  @Test
  public void testMoveToNewPackageCommitted() throws Throwable {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand(myWorkingCopyDir, "a.txt", "A");
    checkin();
    moveToNewPackage(file, "child");
    verifySorted(runSvn("status"), "A child", "A + child" + File.separatorChar + "a.txt", "D a.txt");
  }

  private VirtualFile moveToNewPackage(final VirtualFile file, final String packageName) throws Exception {
    final VirtualFile[] dir = new VirtualFile[1];
    new WriteCommandAction.Simple(myProject) {
      @Override
      public void run() {
        try {
          dir[0] = myWorkingCopyDir.createChildDirectory(this, packageName);
          file.move(this, dir[0]);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }

      }
    }.execute().throwException();
    return dir[0];
  }
}
