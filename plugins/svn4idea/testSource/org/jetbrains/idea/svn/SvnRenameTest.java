package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author yole
 */
public class SvnRenameTest extends SvnTestCase {
  @NonNls private static final String LOG_SEPARATOR = "------------------------------------------------------------------------\r\n";

  @Test
  public void testSimpleRename() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile a = createFileInCommand("a.txt", "test");
    checkin();

    renameFileInCommand(a, "b.txt");
    verify(runSvn("status"), "A + b.txt", "D a.txt");
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
    final RunResult result = runSvn("status");
    verify(result, "R + a.txt", "D aNew.txt", "A + aOld.txt");
  }

  // IDEADEV-16251
  @Test
  public void testRenameAddedPackage() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile dir = createDirInCommand(myWorkingCopyDir, "child");
    createFileInCommand(dir, "a.txt", "content");
    renameFileInCommand(dir, "newchild");
    verify(runSvn("status"), "A newchild", "A newchild\\a.txt");
  }

  // IDEADEV-8091
  @Test
  public void testDoubleRename() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile a = createFileInCommand("a.txt", "test");
    checkin();

    renameFileInCommand(a, "b.txt");
    renameFileInCommand(a, "c.txt");
    verify(runSvn("status"), "A + c.txt", "D a.txt");
  }

  // IDEADEV-15876
  @Test
  public void testRenamePackageWithChildren() throws Exception {
    final VirtualFile child = prepareDirectoriesForRename();

    renameFileInCommand(child, "newchild");
    final RunResult result = runSvn("status");
    verify(result, "D child", "D child\\grandChild", "D child\\grandChild\\b.txt", "D child\\a.txt", "A + newchild");

    List<Change> changes = getAllChanges();
    Assert.assertEquals(4, changes.size());
    sortChanges(changes);
    verifyChange(changes.get(0), "child", "newchild");
    verifyChange(changes.get(1), "child\\a.txt", "newchild\\a.txt");
    verifyChange(changes.get(2), "child\\grandChild", "newchild\\grandChild");
    verifyChange(changes.get(3), "child\\grandChild\\b.txt", "newchild\\grandChild\\b.txt");

    final VirtualFile newChild = myWorkingCopyDir.findChild("newchild");
    assert newChild != null;
    changes = getChangesForFile(newChild.findChild("a.txt"));
    Assert.assertEquals(1, changes.size());
    verifyChange(changes.get(0), "child\\a.txt", "newchild\\a.txt");

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);
    VirtualFile oldChild = myWorkingCopyDir.findChild("child");
    Assert.assertEquals(FileStatus.DELETED, changeListManager.getStatus(oldChild));
  }

  private void sortChanges(final List<Change> changes) {
    Collections.sort(changes, new Comparator<Change>() {
      public int compare(final Change o1, final Change o2) {
        final String p1 = FileUtil.toSystemIndependentName(ChangesUtil.getFilePath(o1).getPath());
        final String p2 = FileUtil.toSystemIndependentName(ChangesUtil.getFilePath(o2).getPath());
        return p1.compareTo(p2);
      }
    });
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

    final RunResult runResult = runSvn("log", "-q", "newchild/a.txt");
    verify(runResult);
    final List<String> lines = StringUtil.split(runResult.stdOut, LOG_SEPARATOR);
    Assert.assertEquals(2, lines.size());
    Assert.assertTrue(lines.get(0).startsWith("r2 |"));
    Assert.assertTrue(lines.get(1).startsWith("r1 |"));
  }

  // IDEADEV-9755
  @Test
  public void testRollbackRenameDir() throws Exception {
    final VirtualFile child = prepareDirectoriesForRename();
    renameFileInCommand(child, "newchild");

    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);
    final Change change = changeListManager.getChange(myWorkingCopyDir.findChild("newchild"));
    Assert.assertNotNull(change);

    SvnVcs.getInstance(myProject).getRollbackEnvironment().rollbackChanges(Collections.singletonList(change));
    Assert.assertFalse(new File(myWorkingCopyDir.getPath(), "newchild").exists());
    Assert.assertTrue(new File(myWorkingCopyDir.getPath(), "child").exists());
  }

  // IDEADEV-7697
  @Test
  public void testMovePackageToParent() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile child = createDirInCommand(myWorkingCopyDir, "child");
    final VirtualFile grandChild = createDirInCommand(child, "grandChild");
    createFileInCommand(grandChild, "a.txt", "a");
    checkin();

    moveFileInCommand(grandChild, myWorkingCopyDir);
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.ensureUpToDate(false);
    final List<Change> changes = new ArrayList<Change>(changeListManager.getDefaultChangeList().getChanges());
    Assert.assertEquals(2, changes.size());
    sortChanges(changes);
    verifyChange(changes.get(0), "child\\grandChild", "grandChild");
    verifyChange(changes.get(1), "child\\grandChild\\a.txt", "grandChild\\a.txt");
  }
}