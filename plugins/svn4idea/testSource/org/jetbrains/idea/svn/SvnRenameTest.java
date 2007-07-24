package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
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
    verifySorted(result, "A + aOld.txt", "D aNew.txt", "R + a.txt");
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

    renameFileInCommand(child, "childnew");
    final RunResult result = runSvn("status");
    verify(result, "D child", "D child\\grandChild", "D child\\grandChild\\b.txt", "D child\\a.txt", "A + childnew");

    List<Change> changes = getAllChanges();
    Assert.assertEquals(4, changes.size());
    sortChanges(changes);
    verifyChange(changes.get(0), "child", "childnew");
    verifyChange(changes.get(1), "child\\a.txt", "childnew\\a.txt");
    verifyChange(changes.get(2), "child\\grandChild", "childnew\\grandChild");
    verifyChange(changes.get(3), "child\\grandChild\\b.txt", "childnew\\grandChild\\b.txt");

    final VirtualFile childnew = myWorkingCopyDir.findChild("childnew");
    assert childnew != null;
    changes = getChangesForFile(childnew.findChild("a.txt"));
    Assert.assertEquals(1, changes.size());
    verifyChange(changes.get(0), "child\\a.txt", "childnew\\a.txt");

    LocalFileSystem.getInstance().refresh(false);   // wait for end of refresh operations initiated from SvnFileSystemListener
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
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);

    moveFileInCommand(grandChild, myWorkingCopyDir);
    LocalFileSystem.getInstance().refresh(false);   // wait for end of refresh operations initiated from SvnFileSystemListener
    changeListManager.ensureUpToDate(false);
    final List<Change> changes = new ArrayList<Change>(changeListManager.getDefaultChangeList().getChanges());
    Assert.assertEquals(2, changes.size());
    sortChanges(changes);
    verifyChange(changes.get(0), "child\\grandChild", "grandChild");
    verifyChange(changes.get(1), "child\\grandChild\\a.txt", "grandChild\\a.txt");
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
    changeListManager.ensureUpToDate(false);
    Assert.assertEquals(FileStatus.UNKNOWN, changeListManager.getStatus(unversioned));

    renameFileInCommand(child, "newchild");
    File childPath = new File(myWorkingCopyDir.getPath(), "child");
    File newChildPath = new File(myWorkingCopyDir.getPath(), "newchild");
    Assert.assertTrue(new File(newChildPath, "a.txt").exists());
    Assert.assertTrue(new File(newChildPath, "u.txt").exists());
    Assert.assertFalse(new File(childPath, "u.txt").exists());

    LocalFileSystem.getInstance().refresh(false);
    changeListManager.ensureUpToDate(false);
    final List<Change> changes = new ArrayList<Change>();
    changes.add(ChangeListManager.getInstance(myProject).getChange(myWorkingCopyDir.findChild("newchild").findChild("a.txt")));
    changes.add(ChangeListManager.getInstance(myProject).getChange(myWorkingCopyDir.findChild("newchild")));

    SvnVcs.getInstance(myProject).getRollbackEnvironment().rollbackChanges(changes);
    Assert.assertTrue(new File(childPath, "a.txt").exists());
    Assert.assertTrue(new File(childPath, "u.txt").exists());
    final File unversionedDirFile = new File(childPath, "uc");
    Assert.assertTrue(unversionedDirFile.exists());
    Assert.assertTrue(new File(unversionedDirFile, "c.txt").exists());
  }

  // IDEA-13824
  @Test
  @Ignore
  public void testRenameFileRenameDir() throws Exception {
    final VirtualFile child = prepareDirectoriesForRename();
    final VirtualFile f = child.findChild("a.txt");
    renameFileInCommand(f, "anew.txt");
    renameFileInCommand(child, "newchild");

    verifySorted(runSvn("status"), "A + newchild", "A + newchild\\anew.txt", "D + newchild\\a.txt",
                 "D child", "D child\\a.txt");

    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    LocalFileSystem.getInstance().refresh(false);   // wait for end of refresh operations initiated from SvnFileSystemListener
    changeListManager.ensureUpToDate(false);
    final List<Change> changes = new ArrayList<Change>(changeListManager.getDefaultChangeList().getChanges());
    final List<VcsException> list = SvnVcs.getInstance(myProject).getCheckinEnvironment().commit(changes, "test");
    Assert.assertEquals(0, list.size());
  }
}
