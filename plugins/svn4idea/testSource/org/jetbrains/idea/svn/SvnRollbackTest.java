// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.LocallyDeletedChange;
import com.intellij.openapi.vcs.changes.SimpleContentRevision;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.ignore.FileGroupInfo;
import org.jetbrains.idea.svn.ignore.SvnPropertyService;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.idea.svn.api.Revision.WORKING;

public class SvnRollbackTest extends SvnTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  @Test
  public void testSimpleRollback() throws Exception {
    final VirtualFile a = createFileInCommand("a.txt", "test");
    checkin();

    VcsTestUtil.editFileInCommand(myProject, a, "tset");
    refreshChanges();

    final Change change = changeListManager.getChange(a);
    Assert.assertNotNull(change);

    assertRollback(Collections.singletonList(change), Collections.emptyList());
  }

  private void assertRollback(List<Change> changes, final List<Change> allowedAfter) throws VcsException {
    final List<VcsException> exceptions = new ArrayList<>();
    vcs.createRollbackEnvironment().rollbackChanges(changes, exceptions, RollbackProgressListener.EMPTY);
    if (! exceptions.isEmpty()) {
      throw exceptions.get(0);
    }

    refreshChanges();

    List<LocalChangeList> lists = changeListManager.getChangeLists();
    final HashSet<Change> afterCopy = new HashSet<>(allowedAfter);
    for (LocalChangeList list : lists) {
      final Collection<Change> listChanges = list.getChanges();
      if (! listChanges.isEmpty()) {
        for (Change change : listChanges) {
          final boolean removed = afterCopy.remove(change);
          Assert.assertTrue(removed);
        }
      }
    }
    Assert.assertTrue(afterCopy.isEmpty());
  }

  @Test
  public void testRollbackMoveDir() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    VcsTestUtil.moveFileInCommand(myProject, tree.mySourceDir, tree.myTargetDir);

    refreshChanges();

    final Change change = assertMove(tree.mySourceDir);
    final Change s1Change = assertMove(tree.myS1File);
    final Change s2Change = assertMove(tree.myS2File);

    assertRollback(Collections.singletonList(change), Collections.emptyList());
  }

  @Test
  public void testRollbackMOveDirVariant() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile unv = createFileInCommand(tree.mySourceDir, "unv.txt", "***");
    final File wasUnversioned = virtualToIoFile(unv);

    VcsTestUtil.moveFileInCommand(myProject, tree.mySourceDir, tree.myTargetDir);

    refreshChanges();

    final Change change = assertMove(tree.mySourceDir);
    final Change s1Change = assertMove(tree.myS1File);
    final Change s2Change = assertMove(tree.myS2File);

    Assert.assertTrue(unv != null);
    Assert.assertTrue(unv.isValid());
    Assert.assertTrue(!FileUtil.filesEqual(virtualToIoFile(unv), wasUnversioned));
    Assert.assertTrue(! wasUnversioned.exists());

    assertRollback(Arrays.asList(change, s2Change), Collections.emptyList());
    Assert.assertTrue(wasUnversioned.exists());
  }

  //IDEA-39943
  @Test
  public void testRollbackWithDeepUnversioned() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    final VirtualFile inner = createDirInCommand(tree.mySourceDir, "inner");
    final VirtualFile innerFile = createFileInCommand(inner, "inInner.txt", "kdfjsdisdjiuewjfew wefn w");
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile deepUnverioned = createFileInCommand(inner, "deepUnverioned.txt", "deepUnverioned");
    final File was = virtualToIoFile(deepUnverioned);

    checkin();
    runAndVerifyStatus("? root" + File.separator + "source" + File.separator + "inner" + File.separator + deepUnverioned.getName());

    VcsTestUtil.renameFileInCommand(myProject, tree.mySourceDir, "newName");

    refreshChanges();

    final Change change = assertRename(tree.mySourceDir);
    final Change s1Change = assertMove(tree.myS1File);
    final Change s2Change = assertMove(tree.myS2File);
    assertMove(inner);
    assertMove(innerFile);

    Assert.assertTrue(!FileUtil.filesEqual(virtualToIoFile(deepUnverioned), was));
    Assert.assertTrue(! was.exists());

    assertRollback(Arrays.asList(change), Collections.emptyList());
    Assert.assertTrue(was.exists());
  }

  @Test
  public void testRollbackDeepEdit() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    final VirtualFile inner = createDirInCommand(tree.mySourceDir, "inner");
    final VirtualFile innerFile = createFileInCommand(inner, "inInner.txt", "kdfjsdisdjiuewjfew wefn w");

    checkin();
    runAndVerifyStatus();

    VcsTestUtil.editFileInCommand(myProject, innerFile, "some content");
    VcsTestUtil.renameFileInCommand(myProject, tree.mySourceDir, "newName");

    refreshChanges();

    final Change change = assertRename(tree.mySourceDir);
    final Change s1Change = assertMove(tree.myS1File);
    final Change s2Change = assertMove(tree.myS2File);
    assertMove(inner);
    final Change innerChange = assertMove(innerFile);

    assertRollback(Arrays.asList(change),
                   Arrays.asList(new Change(innerChange.getBeforeRevision(), innerChange.getBeforeRevision(), FileStatus.MODIFIED)));
  }

  @Test
  public void testRollbackDirRenameWithDeepRenamesAndUnverioned() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    final VirtualFile inner = createDirInCommand(tree.mySourceDir, "inner");
    final VirtualFile inner1 = createDirInCommand(inner, "inner1");
    final VirtualFile inner2 = createDirInCommand(inner1, "inner2");
    final VirtualFile innerFile_ = createFileInCommand(inner1, "inInner38432.txt", "kdfjsdisdjiuewjfew wefn w");
    final VirtualFile inner3 = createDirInCommand(inner2, "inner3");
    final VirtualFile innerFile = createFileInCommand(inner3, "inInner.txt", "kdfjsdisdjiuewjfew wefn w");
    final VirtualFile innerFile1 = createFileInCommand(inner3, "inInner1.txt", "kdfjsdisdjiuewjfew wefn w");
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile deepUNversioned = createFileInCommand(inner3, "deep.txt", "deep");
    final File wasU = virtualToIoFile(deepUNversioned);
    final File wasLowestDir = virtualToIoFile(inner3);
    final File wasInnerFile1 = virtualToIoFile(innerFile1);
    final File wasInnerFile = virtualToIoFile(innerFile);

    checkin();
    runAndVerifyStatus("? root" + File.separator + "source" + File.separator + "inner" +
                      File.separator + "inner1" + File.separator + "inner2" + File.separator +
                      "inner3" + File.separator + "deep.txt");

    VcsTestUtil.editFileInCommand(myProject, innerFile, "some content");
    final File inner2Before = virtualToIoFile(inner2);
    VcsTestUtil.renameFileInCommand(myProject, inner2, "newName2");
    final File wasU2 = virtualToIoFile(deepUNversioned);
    final File inner2After = virtualToIoFile(inner2);
    final File wasInnerFileAfter = virtualToIoFile(innerFile);
    final File wasInnerFile1After = virtualToIoFile(innerFile1);
    final File wasLowestDirAfter = virtualToIoFile(inner3);

    VcsTestUtil.renameFileInCommand(myProject, tree.mySourceDir, "newNameSource");

    Assert.assertTrue(! wasU.exists());
    Assert.assertTrue(! wasU2.exists());

    refreshChanges();

    final Change change = assertRename(tree.mySourceDir);
    final Change s1Change = assertMove(tree.myS1File);
    final Change s2Change = assertMove(tree.myS2File);
    final Change inner2Change = assertMove(inner2);
    assertMove(inner);
    final Change innerChange = assertMove(innerFile);

    final Change fantomDelete1 = new Change(new SimpleContentRevision("1", VcsUtil.getFilePath(wasLowestDir, true), "2"),
                                            new SimpleContentRevision("1", VcsUtil.getFilePath(wasLowestDirAfter, true), "2"));
    final Change fantomDelete2 = new Change(new SimpleContentRevision("1", VcsUtil.getFilePath(wasInnerFile1, false), "2"),
                                            new SimpleContentRevision("1", VcsUtil.getFilePath(wasInnerFile1After, false),
                                                                      WORKING.toString()));

    assertRollback(Arrays.asList(change),
                   Arrays.asList(new Change(new SimpleContentRevision("1", VcsUtil.getFilePath(wasInnerFile, false), "2"),
                                          new SimpleContentRevision("1", VcsUtil.getFilePath(wasInnerFileAfter, false),
                                                                    WORKING.toString())),
                               new Change(new SimpleContentRevision("1", VcsUtil.getFilePath(inner2Before, true), "2"),
                                          new SimpleContentRevision("1", VcsUtil.getFilePath(inner2After, true),
                                                                    WORKING.toString())),
                               fantomDelete1, fantomDelete2));
    Assert.assertTrue(wasU2.exists());
  }

  @Test
  public void testKeepDeepProperty() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    final VirtualFile inner = createDirInCommand(tree.mySourceDir, "inner");
    final VirtualFile innerFile = createFileInCommand(inner, "inInner.txt", "kdfjsdisdjiuewjfew wefn w");

    checkin();
    runAndVerifyStatus();

    final File fileBefore = virtualToIoFile(innerFile);
    setProperty(fileBefore, "abc", "cde");
    Assert.assertEquals("cde", getProperty(virtualToIoFile(innerFile), "abc"));
    final File innerBefore = virtualToIoFile(inner);
    VcsTestUtil.renameFileInCommand(myProject, inner, "innerNew");
    final File innerAfter = virtualToIoFile(inner);
    final File fileAfter = virtualToIoFile(innerFile);
    VcsTestUtil.renameFileInCommand(myProject, tree.mySourceDir, "newName");

    refreshChanges();

    final Change change = assertRename(tree.mySourceDir);
    final Change s1Change = assertMove(tree.myS1File);
    final Change s2Change = assertMove(tree.myS2File);
    assertMove(inner);
    final Change innerChange = assertMove(innerFile);
    Assert.assertEquals("cde", getProperty(virtualToIoFile(innerFile), "abc"));

    assertRollback(Arrays.asList(change),
                   Arrays.asList(new Change(new SimpleContentRevision("1", VcsUtil.getFilePath(innerBefore, true), "2"),
                                          new SimpleContentRevision("1", VcsUtil.getFilePath(innerAfter, true),
                                                                    WORKING.toString())),
                               new Change(new SimpleContentRevision("1", VcsUtil.getFilePath(fileBefore, false), "2"),
                                          new SimpleContentRevision("1", VcsUtil.getFilePath(fileAfter, false),
                                                                    WORKING.toString()))));
    Assert.assertEquals("cde", getProperty(fileAfter, "abc"));
  }

  private String getProperty(File file, String name) throws SvnBindException {
    PropertyValue value = vcs.getFactory(file).createPropertyClient().getProperty(Target.on(file), name, false, WORKING);

    return PropertyValue.toString(value);
  }

  private void setProperty(final File file, final String name, final String value) throws SvnBindException {
    vcs.getFactory(file).createPropertyClient().setProperty(file, name, PropertyValue.create(value), Depth.EMPTY, true);
  }

  @Test
  public void testRollbackDelete() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final FilePath fpSource = VcsUtil.getFilePath(virtualToIoFile(tree.mySourceDir), true);
    final FilePath fpT11 = VcsUtil.getFilePath(virtualToIoFile(tree.myTargetFiles.get(0)), false);
    VcsTestUtil.deleteFileInCommand(myProject, tree.mySourceDir);
    VcsTestUtil.deleteFileInCommand(myProject, tree.myTargetFiles.get(0));

    refreshChanges();

    final Change change = assertDelete(fpSource);
    final Change t11Change = assertDelete(fpT11);

    assertRollback(Arrays.asList(change, t11Change), Collections.emptyList());
  }

  private Change assertDelete(FilePath fpSource) {
    final Change change = changeListManager.getChange(fpSource);
    Assert.assertNotNull(change);
    Assert.assertNull(change.getAfterRevision());
    return change;
  }

  @Test
  public void testRollbackAdd() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final VirtualFile newDir = createDirInCommand(tree.mySourceDir, "newDir");
    final VirtualFile inNewDir = createFileInCommand(newDir, "f.txt", "12345");
    final VirtualFile inSource = createFileInCommand(tree.myTargetDir, "newF.txt", "54321");

    Assert.assertTrue(newDir != null && inNewDir != null && inSource != null);
    refreshChanges();

    final Change change = assertAdd(newDir);
    final Change inNewDirChange = assertAdd(inNewDir);
    final Change inSourceChange = assertAdd(inSource);

    assertRollback(Arrays.asList(change, inSourceChange), Collections.emptyList());
  }

  private Change assertAdd(VirtualFile newDir) {
    final Change change = changeListManager.getChange(newDir);
    Assert.assertNotNull(change);
    Assert.assertNull(change.getBeforeRevision());
    return change;
  }

  // move directory with unversioned dir + check edit
  @Test
  public void testRollbackRenameDirWithUnversionedDir() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final String editedText = "s1 edited";
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, editedText);
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile unverionedDir = createDirInCommand(tree.mySourceDir, "unverionedDir");
    final String unvText = "unv content";
    final VirtualFile unvFile = createFileInCommand(unverionedDir, "childFile", unvText);
    final File wasUnvDir = virtualToIoFile(unverionedDir);
    final File wasUnvFile = virtualToIoFile(unvFile);

    VcsTestUtil.renameFileInCommand(myProject, tree.mySourceDir, "renamed");

    refreshChanges();

    final Change dirChange = assertRename(tree.mySourceDir);
    final Change s1Change = assertMove(tree.myS1File);

    FileStatus status = changeListManager.getStatus(unverionedDir);
    Assert.assertEquals(FileStatus.UNKNOWN, status);
    Assert.assertTrue(! wasUnvDir.exists());

    FileStatus fileStatus = changeListManager.getStatus(unvFile);
    Assert.assertEquals(FileStatus.UNKNOWN, fileStatus);
    Assert.assertTrue(! wasUnvFile.exists());

    assertRollback(Collections.singletonList(dirChange), Collections.singletonList(new Change(s1Change.getBeforeRevision(),
                                                                                              s1Change.getBeforeRevision(),
                                                                                              FileStatus.MODIFIED)));
    Assert.assertTrue(wasUnvDir.exists());
    Assert.assertTrue(wasUnvFile.exists());
  }

  @Test
  public void testRollbackDirWithIgnored() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    VirtualFile ignored = createFileInCommand(tree.mySourceDir, "ign.txt", "ignored");
    final File wasIgnored = virtualToIoFile(ignored);
    final FileGroupInfo groupInfo = new FileGroupInfo();
    groupInfo.onFileEnabled(ignored);
    SvnPropertyService.doAddToIgnoreProperty(vcs, false, new VirtualFile[]{ignored}, groupInfo);

    refreshChanges();
    Assert.assertTrue(FileStatus.IGNORED.equals(changeListManager.getStatus(ignored)));

    VcsTestUtil.renameFileInCommand(myProject, tree.mySourceDir, "renamed");

    refreshChanges();

    final Change dirChange = assertRename(tree.mySourceDir);
    final Change s1Change = assertMove(tree.myS1File);
    final Change s2Change = assertMove(tree.myS2File);
    Assert.assertTrue(! wasIgnored.exists());
    Assert.assertTrue(FileStatus.IGNORED.equals(changeListManager.getStatus(ignored)));

    assertRollback(Collections.singletonList(dirChange), Collections.emptyList());
    ignored = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(wasIgnored);
    // ignored property was not committed
    Assert.assertTrue(FileStatus.UNKNOWN.equals(changeListManager.getStatus(ignored)));
    Assert.assertTrue(wasIgnored.exists());
  }

  @Test
  public void testRollbackDirWithCommittedIgnored() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    VirtualFile ignored = createFileInCommand(tree.mySourceDir, "ign.txt", "ignored");
    final File wasIgnored = virtualToIoFile(ignored);
    final FileGroupInfo groupInfo = new FileGroupInfo();
    groupInfo.onFileEnabled(ignored);
    SvnPropertyService.doAddToIgnoreProperty(vcs, false, new VirtualFile[]{ignored}, groupInfo);
    checkin();

    refreshChanges();
    Assert.assertTrue(FileStatus.IGNORED.equals(changeListManager.getStatus(ignored)));

    VcsTestUtil.renameFileInCommand(myProject, tree.mySourceDir, "renamed");

    refreshChanges();

    final Change dirChange = assertRename(tree.mySourceDir);
    final Change s1Change = assertMove(tree.myS1File);
    final Change s2Change = assertMove(tree.myS2File);
    Assert.assertTrue(! wasIgnored.exists());
    Assert.assertTrue(FileStatus.IGNORED.equals(changeListManager.getStatus(ignored)));

    assertRollback(Collections.singletonList(dirChange), Collections.emptyList());
    ignored = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(wasIgnored);
    // ignored property was not committed
    Assert.assertTrue(FileStatus.IGNORED.equals(changeListManager.getStatus(ignored)));
    Assert.assertTrue(wasIgnored.exists());
  }

  @Test
  public void testListAllChangesForRevert() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final String editedText = "s1 edited";
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, editedText);

    VcsTestUtil.renameFileInCommand(myProject, tree.mySourceDir, "renamed");

    refreshChanges();

    final Change dirChange = assertRename(tree.mySourceDir);
    final Change s1Change = assertMove(tree.myS1File);
    final Change s2Change = assertMove(tree.myS2File);

    assertRollback(Arrays.asList(dirChange, s1Change, s2Change), Collections.emptyList());
  }

  @Test
  public void testKeepOneUnderRenamed() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    final File was2 = virtualToIoFile(tree.myS2File);

    final String editedText = "s1 edited";
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, editedText);
    VcsTestUtil.editFileInCommand(myProject, tree.myS2File, "s2 edited");

    VcsTestUtil.renameFileInCommand(myProject, tree.mySourceDir, "renamed");

    refreshChanges();

    final Change dirChange = assertRename(tree.mySourceDir);
    final Change s1Change = assertMove(tree.myS1File);
    final Change s2Change = assertMove(tree.myS2File);

    final FilePath fp = VcsUtil.getFilePath(was2, false);
    assertRollback(Arrays.asList(dirChange, s1Change), Arrays.asList(new Change(
      new SimpleContentRevision("1", fp, "1"), new SimpleContentRevision("1", fp, WORKING.toString()))));
  }

  @Test
  public void testRollbackLocallyDeletedSimple() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final File wasFile = virtualToIoFile(tree.myS1File);
    VcsTestUtil.deleteFileInCommand(myProject, tree.myS1File);

    refreshChanges();

    final List<LocallyDeletedChange> deletedFiles = changeListManager.getDeletedFiles();
    Assert.assertNotNull(deletedFiles);
    Assert.assertTrue(deletedFiles.size() == 1);
    Assert.assertEquals(wasFile, deletedFiles.get(0).getPath().getIOFile());

    assertRollbackLocallyDeleted(Collections.singletonList(deletedFiles.get(0).getPath()), Collections.emptyList());
  }

  @Test
  public void testRollbackLocallyDeletedSimpleDir() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final File wasFile = virtualToIoFile(tree.mySourceDir);
    final File wasFileS1 = virtualToIoFile(tree.myS1File);
    final File wasFileS2 = virtualToIoFile(tree.myS2File);
    VcsTestUtil.deleteFileInCommand(myProject, tree.mySourceDir);

    refreshChanges();

    final List<LocallyDeletedChange> deletedFiles = changeListManager.getDeletedFiles();
    Assert.assertNotNull(deletedFiles);
    Assert.assertTrue(deletedFiles.size() == 3);

    final Set<File> files = new HashSet<>();
    files.add(wasFile);
    files.add(wasFileS1);
    files.add(wasFileS2);

    for (LocallyDeletedChange file : deletedFiles) {
      files.remove(file.getPath().getIOFile());
    }
    Assert.assertTrue(files.isEmpty());

    assertRollbackLocallyDeleted(Collections.singletonList(VcsUtil.getFilePath(wasFile, true)), Collections.emptyList());
  }

  @Test
  public void testRollbackAddedLocallyDeleted() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    VirtualFile f1 = createFileInCommand(tree.mySourceDir, "f1", "4");
    VirtualFile dir = createDirInCommand(tree.mySourceDir, "dirrr");
    VirtualFile f2 = createFileInCommand(dir, "f2", "411");

    refreshChanges();

    assertAdd(f1);
    assertAdd(dir);
    assertAdd(f2);

    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final File wasFile1 = virtualToIoFile(f1);
    final File wasFile2 = virtualToIoFile(dir);
    final File wasFile3 = virtualToIoFile(f2);

    VcsTestUtil.deleteFileInCommand(myProject, f1);
    VcsTestUtil.deleteFileInCommand(myProject, dir);

    refreshChanges();

    final List<LocallyDeletedChange> deletedFiles = changeListManager.getDeletedFiles();
    Assert.assertNotNull(deletedFiles);
    Assert.assertTrue(deletedFiles.size() == 3);
    final Set<File> files = new HashSet<>();
    files.add(wasFile1);
    files.add(wasFile2);
    files.add(wasFile3);

    Assert.assertTrue(files.contains(deletedFiles.get(0).getPath().getIOFile()));
    Assert.assertTrue(files.contains(deletedFiles.get(1).getPath().getIOFile()));
    Assert.assertTrue(files.contains(deletedFiles.get(2).getPath().getIOFile()));

    assertRollbackLocallyDeleted(Arrays.asList(VcsUtil.getFilePath(wasFile2, true), VcsUtil.getFilePath(wasFile1, false)),
                                 Collections.emptyList());
  }

  @Test
  public void testRollbackMovedDirectoryLocallyDeleted() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    final File wasInitially = virtualToIoFile(tree.mySourceDir);
    Assert.assertTrue(wasInitially.exists());

    VcsTestUtil.moveFileInCommand(myProject, tree.mySourceDir, tree.myTargetDir);
    Assert.assertTrue(!wasInitially.exists());

    refreshChanges();

    final Change movedChange = assertMove(tree.mySourceDir);
    final File was = virtualToIoFile(tree.mySourceDir);
    Assert.assertNotSame(wasInitially, was);
    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    VcsTestUtil.deleteFileInCommand(myProject, tree.mySourceDir);

    runAndVerifyStatusSorted(
      "! root" + File.separator + "target" + File.separator + "source",
      "! root" + File.separator + "target" + File.separator + "source" + File.separator + "s1.txt",
      "! root" + File.separator + "target" + File.separator + "source" + File.separator + "s2.txt",
           "D root" + File.separator + "source",
           "D root" + File.separator + "source" + File.separator + "s1.txt",
           "D root" + File.separator + "source" + File.separator + "s2.txt"
    );

    assertRollbackLocallyDeleted(Collections.singletonList(VcsUtil.getFilePath(was, true)), Collections.emptyList());
    runAndVerifyStatusSorted("D root" + File.separator + "source",
               "D root" + File.separator + "source" + File.separator + "s1.txt",
               "D root" + File.separator + "source" + File.separator + "s2.txt");
  }

  private void assertRollbackLocallyDeleted(final List<FilePath> locally, final List<FilePath> allowed) {
    final List<VcsException> exceptions = new ArrayList<>();
    vcs.createRollbackEnvironment().rollbackMissingFileDeletion(locally, exceptions, RollbackProgressListener.EMPTY);
    Assert.assertTrue(exceptions.isEmpty());

    refreshChanges();

    final List<LocallyDeletedChange> deletedFiles = changeListManager.getDeletedFiles();
    if (allowed == null || allowed.isEmpty()) {
      Assert.assertTrue(deletedFiles == null || deletedFiles.isEmpty());
    }
    final ArrayList<FilePath> copy = new ArrayList<>(allowed);
    for (LocallyDeletedChange file : deletedFiles) {
      copy.remove(file.getPath());
    }
    Assert.assertTrue(copy.isEmpty());
  }

  private Change assertMove(final VirtualFile file) {
    final Change change = changeListManager.getChange(file);
    Assert.assertNotNull(change);
    Assert.assertTrue(change.isMoved());
    return change;
  }

  private Change assertRename(final VirtualFile file) {
    final Change change = changeListManager.getChange(file);
    Assert.assertNotNull(change);
    Assert.assertTrue(change.isRenamed());
    return change;
  }
}
