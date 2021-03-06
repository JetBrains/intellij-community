// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.LocallyDeletedChange;
import com.intellij.openapi.vcs.changes.SimpleContentRevision;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.ignore.FileGroupInfo;
import org.jetbrains.idea.svn.ignore.SvnPropertyService;
import org.jetbrains.idea.svn.properties.PropertyValue;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.testFramework.UsefulTestCase.assertDoesntExist;
import static com.intellij.testFramework.UsefulTestCase.assertExists;
import static com.intellij.util.containers.ContainerUtil.isEmpty;
import static com.intellij.util.lang.CompoundRuntimeException.throwIfNotEmpty;
import static com.intellij.vcsUtil.VcsUtil.getFilePath;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.jetbrains.idea.svn.api.Revision.WORKING;
import static org.junit.Assert.*;

public class SvnRollbackTest extends SvnTestCase {
  @Override
  @Before
  public void before() throws Exception {
    super.before();

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  @Test
  public void testSimpleRollback() throws Exception {
    final VirtualFile a = createFileInCommand("a.txt", "test");
    checkin();

    editFileInCommand(a, "tset");
    refreshChanges();

    final Change change = changeListManager.getChange(a);
    assertNotNull(change);

    assertRollback(singletonList(change), emptyList());
  }

  @Test
  public void testRollbackMoveDir() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    moveFileInCommand(tree.mySourceDir, tree.myTargetDir);
    refreshChanges();

    final Change change = assertMove(tree.mySourceDir);
    assertMove(tree.myS1File);
    assertMove(tree.myS2File);

    assertRollback(singletonList(change), emptyList());
  }

  @Test
  public void testRollbackMOveDirVariant() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    final VirtualFile unv = createFileInCommand(tree.mySourceDir, "unv.txt", "***");
    final File wasUnversioned = virtualToIoFile(unv);
    moveFileInCommand(tree.mySourceDir, tree.myTargetDir);
    refreshChanges();

    final Change change = assertMove(tree.mySourceDir);
    assertMove(tree.myS1File);
    final Change s2Change = assertMove(tree.myS2File);
    assertTrue(unv.isValid());
    assertTrue(!filesEqual(virtualToIoFile(unv), wasUnversioned));
    assertDoesntExist(wasUnversioned);

    assertRollback(asList(change, s2Change), emptyList());

    assertExists(wasUnversioned);
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
    runAndVerifyStatus("? root/source/inner/" + deepUnverioned.getName());
    update();

    renameFileInCommand(tree.mySourceDir, "newName");
    refreshChanges();

    final Change change = assertRename(tree.mySourceDir);
    assertMove(tree.myS1File);
    assertMove(tree.myS2File);
    assertMove(inner);
    assertMove(innerFile);

    assertTrue(!filesEqual(virtualToIoFile(deepUnverioned), was));
    assertDoesntExist(was);

    assertRollback(singletonList(change), emptyList());

    assertExists(was);
  }

  @Test
  public void testRollbackDeepEdit() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final VirtualFile inner = createDirInCommand(tree.mySourceDir, "inner");
    final VirtualFile innerFile = createFileInCommand(inner, "inInner.txt", "kdfjsdisdjiuewjfew wefn w");
    checkin();
    runAndVerifyStatus();
    update();

    editFileInCommand(innerFile, "some content");
    renameFileInCommand(tree.mySourceDir, "newName");
    refreshChanges();

    final Change change = assertRename(tree.mySourceDir);
    assertMove(tree.myS1File);
    assertMove(tree.myS2File);
    assertMove(inner);
    final Change innerChange = assertMove(innerFile);

    assertRollback(singletonList(change),
                   singletonList(new Change(innerChange.getBeforeRevision(), innerChange.getBeforeRevision(), FileStatus.MODIFIED)));
  }

  @Test
  public void testRollbackDirRenameWithDeepRenamesAndUnverioned() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final VirtualFile inner = createDirInCommand(tree.mySourceDir, "inner");
    final VirtualFile inner1 = createDirInCommand(inner, "inner1");
    final VirtualFile inner2 = createDirInCommand(inner1, "inner2");
    createFileInCommand(inner1, "inInner38432.txt", "kdfjsdisdjiuewjfew wefn w");
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
    runAndVerifyStatus("? root/source/inner/inner1/inner2/inner3/deep.txt");
    update();

    editFileInCommand(innerFile, "some content");
    final File inner2Before = virtualToIoFile(inner2);
    renameFileInCommand(inner2, "newName2");
    final File wasU2 = virtualToIoFile(deepUNversioned);
    final File inner2After = virtualToIoFile(inner2);
    final File wasInnerFileAfter = virtualToIoFile(innerFile);
    final File wasInnerFile1After = virtualToIoFile(innerFile1);
    final File wasLowestDirAfter = virtualToIoFile(inner3);

    renameFileInCommand(tree.mySourceDir, "newNameSource");
    assertDoesntExist(wasU);
    assertDoesntExist(wasU2);
    refreshChanges();

    final Change change = assertRename(tree.mySourceDir);
    assertMove(tree.myS1File);
    assertMove(tree.myS2File);
    assertMove(inner2);
    assertMove(inner);
    assertMove(innerFile);

    final Change fantomDelete1 = new Change(new SimpleContentRevision("1", getFilePath(wasLowestDir, true), "2"),
                                            new SimpleContentRevision("1", getFilePath(wasLowestDirAfter, true), "2"));
    final Change fantomDelete2 = new Change(new SimpleContentRevision("1", getFilePath(wasInnerFile1, false), "2"),
                                            new SimpleContentRevision("1", getFilePath(wasInnerFile1After, false), WORKING.toString()));

    assertRollback(
      singletonList(change),
      asList(new Change(new SimpleContentRevision("1", getFilePath(wasInnerFile, false), "2"),
                        new SimpleContentRevision("1", getFilePath(wasInnerFileAfter, false), WORKING.toString())),
             new Change(new SimpleContentRevision("1", getFilePath(inner2Before, true), "2"),
                        new SimpleContentRevision("1", getFilePath(inner2After, true), WORKING.toString())), fantomDelete1, fantomDelete2)
    );

    assertExists(wasU2);
  }

  @Test
  public void testKeepDeepProperty() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final VirtualFile inner = createDirInCommand(tree.mySourceDir, "inner");
    final VirtualFile innerFile = createFileInCommand(inner, "inInner.txt", "kdfjsdisdjiuewjfew wefn w");
    checkin();
    runAndVerifyStatus();
    update();

    final File fileBefore = virtualToIoFile(innerFile);
    setProperty(fileBefore, "abc", "cde");
    assertEquals("cde", getProperty(virtualToIoFile(innerFile), "abc"));
    final File innerBefore = virtualToIoFile(inner);
    renameFileInCommand(inner, "innerNew");
    final File innerAfter = virtualToIoFile(inner);
    final File fileAfter = virtualToIoFile(innerFile);
    renameFileInCommand(tree.mySourceDir, "newName");
    refreshChanges();

    final Change change = assertRename(tree.mySourceDir);
    assertMove(tree.myS1File);
    assertMove(tree.myS2File);
    assertMove(inner);
    assertMove(innerFile);
    assertEquals("cde", getProperty(virtualToIoFile(innerFile), "abc"));

    assertRollback(
      singletonList(change),
      asList(new Change(new SimpleContentRevision("1", getFilePath(innerBefore, true), "2"),
                        new SimpleContentRevision("1", getFilePath(innerAfter, true), WORKING.toString())),
             new Change(new SimpleContentRevision("1", getFilePath(fileBefore, false), "2"),
                        new SimpleContentRevision("1", getFilePath(fileAfter, false), WORKING.toString())))
    );

    assertEquals("cde", getProperty(fileAfter, "abc"));
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

    final FilePath fpSource = getFilePath(virtualToIoFile(tree.mySourceDir), true);
    final FilePath fpT11 = getFilePath(virtualToIoFile(tree.myTargetFiles.get(0)), false);
    deleteFileInCommand(tree.mySourceDir);
    deleteFileInCommand(tree.myTargetFiles.get(0));
    refreshChanges();

    final Change change = assertDelete(fpSource);
    final Change t11Change = assertDelete(fpT11);

    assertRollback(asList(change, t11Change), emptyList());
  }

  @Test
  public void testRollbackAdd() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final VirtualFile newDir = createDirInCommand(tree.mySourceDir, "newDir");
    final VirtualFile inNewDir = createFileInCommand(newDir, "f.txt", "12345");
    final VirtualFile inSource = createFileInCommand(tree.myTargetDir, "newF.txt", "54321");
    assertTrue(newDir != null && inNewDir != null && inSource != null);
    refreshChanges();

    final Change change = assertAdd(newDir);
    assertAdd(inNewDir);
    final Change inSourceChange = assertAdd(inSource);

    assertRollback(asList(change, inSourceChange), emptyList());
  }

  // move directory with unversioned dir + check edit
  @Test
  public void testRollbackRenameDirWithUnversionedDir() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final String editedText = "s1 edited";
    editFileInCommand(tree.myS1File, editedText);

    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    final VirtualFile unverionedDir = createDirInCommand(tree.mySourceDir, "unverionedDir");
    final String unvText = "unv content";
    final VirtualFile unvFile = createFileInCommand(unverionedDir, "childFile", unvText);
    final File wasUnvDir = virtualToIoFile(unverionedDir);
    final File wasUnvFile = virtualToIoFile(unvFile);
    renameFileInCommand(tree.mySourceDir, "renamed");
    refreshChanges();

    final Change dirChange = assertRename(tree.mySourceDir);
    final Change s1Change = assertMove(tree.myS1File);
    assertEquals(FileStatus.UNKNOWN, changeListManager.getStatus(unverionedDir));
    assertDoesntExist(wasUnvDir);
    assertEquals(FileStatus.UNKNOWN, changeListManager.getStatus(unvFile));
    assertDoesntExist(wasUnvFile);

    assertRollback(singletonList(dirChange),
                   singletonList(new Change(s1Change.getBeforeRevision(), s1Change.getBeforeRevision(), FileStatus.MODIFIED)));

    assertExists(wasUnvDir);
    assertExists(wasUnvFile);
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

    assertEquals(FileStatus.IGNORED, changeListManager.getStatus(ignored));

    renameFileInCommand(tree.mySourceDir, "renamed");
    refreshChanges();

    final Change dirChange = assertRename(tree.mySourceDir);
    assertMove(tree.myS1File);
    assertMove(tree.myS2File);
    assertDoesntExist(wasIgnored);
    assertEquals(FileStatus.IGNORED, changeListManager.getStatus(ignored));

    assertRollback(singletonList(dirChange), emptyList());

    ignored = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(wasIgnored);
    // ignored property was not committed
    assertEquals(FileStatus.UNKNOWN, changeListManager.getStatus(ignored));
    assertExists(wasIgnored);
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
    update();

    assertEquals(FileStatus.IGNORED, changeListManager.getStatus(ignored));

    renameFileInCommand(tree.mySourceDir, "renamed");
    refreshChanges();

    final Change dirChange = assertRename(tree.mySourceDir);
    assertMove(tree.myS1File);
    assertMove(tree.myS2File);
    assertDoesntExist(wasIgnored);
    assertEquals(FileStatus.IGNORED, changeListManager.getStatus(ignored));

    assertRollback(singletonList(dirChange), emptyList());

    ignored = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(wasIgnored);
    // ignored property was committed
    assertEquals(FileStatus.IGNORED, changeListManager.getStatus(ignored));
    assertExists(wasIgnored);
  }

  @Test
  public void testListAllChangesForRevert() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final String editedText = "s1 edited";
    editFileInCommand(tree.myS1File, editedText);
    renameFileInCommand(tree.mySourceDir, "renamed");
    refreshChanges();

    final Change dirChange = assertRename(tree.mySourceDir);
    final Change s1Change = assertMove(tree.myS1File);
    final Change s2Change = assertMove(tree.myS2File);

    assertRollback(asList(dirChange, s1Change, s2Change), emptyList());
  }

  @Test
  public void testKeepOneUnderRenamed() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final File was2 = virtualToIoFile(tree.myS2File);
    editFileInCommand(tree.myS1File, "s1 edited");
    editFileInCommand(tree.myS2File, "s2 edited");
    renameFileInCommand(tree.mySourceDir, "renamed");
    refreshChanges();

    final Change dirChange = assertRename(tree.mySourceDir);
    final Change s1Change = assertMove(tree.myS1File);
    assertMove(tree.myS2File);

    final FilePath fp = getFilePath(was2, false);
    assertRollback(asList(dirChange, s1Change), singletonList(
      new Change(new SimpleContentRevision("1", fp, "1"), new SimpleContentRevision("1", fp, WORKING.toString()))));
  }

  @Test
  public void testRollbackLocallyDeletedSimple() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final File wasFile = virtualToIoFile(tree.myS1File);
    deleteFileInCommand(tree.myS1File);
    refreshChanges();

    final List<LocallyDeletedChange> deletedFiles = changeListManager.getDeletedFiles();
    assertNotNull(deletedFiles);
    assertEquals(1, deletedFiles.size());
    assertEquals(wasFile, deletedFiles.get(0).getPath().getIOFile());

    assertRollbackLocallyDeleted(singletonList(deletedFiles.get(0).getPath()), emptyList());
  }

  @Test
  public void testRollbackLocallyDeletedSimpleDir() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final File wasFile = virtualToIoFile(tree.mySourceDir);
    final File wasFileS1 = virtualToIoFile(tree.myS1File);
    final File wasFileS2 = virtualToIoFile(tree.myS2File);
    deleteFileInCommand(tree.mySourceDir);
    refreshChanges();

    final List<LocallyDeletedChange> deletedFiles = changeListManager.getDeletedFiles();
    assertNotNull(deletedFiles);
    assertEquals(3, deletedFiles.size());

    final Set<File> files = ContainerUtil.newHashSet(wasFile, wasFileS1, wasFileS2);
    for (LocallyDeletedChange file : deletedFiles) {
      files.remove(file.getPath().getIOFile());
    }
    assertTrue(files.isEmpty());

    assertRollbackLocallyDeleted(singletonList(getFilePath(wasFile, true)), emptyList());
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
    deleteFileInCommand(f1);
    deleteFileInCommand(dir);
    refreshChanges();

    final List<LocallyDeletedChange> deletedFiles = changeListManager.getDeletedFiles();
    assertNotNull(deletedFiles);
    assertEquals(3, deletedFiles.size());
    final Set<File> files = ContainerUtil.newHashSet(wasFile1, wasFile2, wasFile3);
    assertTrue(files.contains(deletedFiles.get(0).getPath().getIOFile()));
    assertTrue(files.contains(deletedFiles.get(1).getPath().getIOFile()));
    assertTrue(files.contains(deletedFiles.get(2).getPath().getIOFile()));

    assertRollbackLocallyDeleted(asList(getFilePath(wasFile2, true), getFilePath(wasFile1, false)), emptyList());
  }

  @Test
  public void testRollbackMovedDirectoryLocallyDeleted() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final File wasInitially = virtualToIoFile(tree.mySourceDir);
    assertExists(wasInitially);
    moveFileInCommand(tree.mySourceDir, tree.myTargetDir);
    assertDoesntExist(wasInitially);
    refreshChanges();

    assertMove(tree.mySourceDir);
    final File was = virtualToIoFile(tree.mySourceDir);
    assertNotSame(wasInitially, was);

    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    deleteFileInCommand(tree.mySourceDir);
    runAndVerifyStatus(
      "D root/source",
      "> moved to root/target/source",
      "D root/source/s1.txt",
      "D root/source/s2.txt",
      "! root/target/source",
      "! root/target/source/s1.txt",
      "! root/target/source/s2.txt"
    );

    assertRollbackLocallyDeleted(singletonList(getFilePath(was, true)), emptyList());

    runAndVerifyStatusSorted("D root/source", "D root/source/s1.txt", "D root/source/s2.txt");
  }

  private void assertRollback(List<Change> changes, final List<Change> allowedAfter) throws VcsException {
    rollback(changes);
    refreshChanges();

    List<LocalChangeList> lists = changeListManager.getChangeLists();
    final HashSet<Change> afterCopy = new HashSet<>(allowedAfter);
    for (LocalChangeList list : lists) {
      for (Change change : list.getChanges()) {
        assertTrue(afterCopy.remove(change));
      }
    }
    assertTrue(afterCopy.isEmpty());
  }

  private void assertRollbackLocallyDeleted(final List<FilePath> locally, final List<FilePath> allowed) throws VcsException {
    final List<VcsException> exceptions = new ArrayList<>();
    vcs.createRollbackEnvironment().rollbackMissingFileDeletion(locally, exceptions, RollbackProgressListener.EMPTY);
    throwIfNotEmpty(exceptions);
    refreshChanges();

    final List<LocallyDeletedChange> deletedFiles = changeListManager.getDeletedFiles();
    if (allowed == null || allowed.isEmpty()) {
      assertTrue(isEmpty(deletedFiles));
    }
    final ArrayList<FilePath> copy = new ArrayList<>(allowed);
    for (LocallyDeletedChange file : deletedFiles) {
      copy.remove(file.getPath());
    }
    assertTrue(copy.isEmpty());
  }

  private Change assertAdd(VirtualFile newDir) {
    final Change change = changeListManager.getChange(newDir);
    assertNotNull(change);
    assertNull(change.getBeforeRevision());
    return change;
  }

  private Change assertDelete(FilePath fpSource) {
    final Change change = changeListManager.getChange(fpSource);
    assertNotNull(change);
    assertNull(change.getAfterRevision());
    return change;
  }

  private Change assertMove(final VirtualFile file) {
    final Change change = changeListManager.getChange(file);
    assertNotNull(change);
    assertTrue(change.isMoved());
    return change;
  }

  private Change assertRename(final VirtualFile file) {
    final Change change = changeListManager.getChange(file);
    assertNotNull(change);
    assertTrue(change.isRenamed());
    return change;
  }
}
