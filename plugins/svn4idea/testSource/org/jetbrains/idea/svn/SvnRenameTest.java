package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
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
  @Ignore
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
    Collections.sort(changes, new Comparator<Change>() {
      public int compare(final Change o1, final Change o2) {
        final String p1 = FileUtil.toSystemIndependentName(o1.getBeforeRevision().getFile().getPath());
        final String p2 = FileUtil.toSystemIndependentName(o2.getBeforeRevision().getFile().getPath());
        return p1.compareTo(p2);
      }
    });
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

  private VirtualFile prepareDirectoriesForRename() throws IOException {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile child = createDirInCommand(myWorkingCopyDir, "child");
    final VirtualFile grandChild = createDirInCommand(child, "grandChild");
    createFileInCommand(child, "a.txt", "a");
    createFileInCommand(grandChild, "b.txt", "b");
    checkin();
    return child;
  }

  private void verifyChange(final Change c, final String beforePath, final String afterPath) {
    verifyRevision(c.getBeforeRevision(), beforePath);
    verifyRevision(c.getAfterRevision(), afterPath);
  }

  private void verifyRevision(final ContentRevision beforeRevision, final String beforePath) {
    File beforeFile = new File(myWorkingCopyDir.getPath(), beforePath);
    String beforeFullPath = FileUtil.toSystemIndependentName(beforeFile.getPath());
    final String beforeRevPath = FileUtil.toSystemIndependentName(beforeRevision.getFile().getPath());
    Assert.assertEquals(beforeFullPath, beforeRevPath);
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

}