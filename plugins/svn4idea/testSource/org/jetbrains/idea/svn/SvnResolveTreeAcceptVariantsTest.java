// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import one.util.streamex.StreamEx;
import org.jetbrains.idea.svn.TreeConflictData.DirToDir;
import org.jetbrains.idea.svn.TreeConflictData.DirToFile;
import org.jetbrains.idea.svn.TreeConflictData.FileToDir;
import org.jetbrains.idea.svn.TreeConflictData.FileToFile;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.treeConflict.SvnTreeConflictResolver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.stream.Stream;

import static com.intellij.openapi.vfs.VfsUtilCore.getRelativePath;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.UsefulTestCase.assertExists;
import static com.intellij.util.containers.ContainerUtil.ar;
import static com.intellij.vcsUtil.VcsUtil.getFilePath;
import static java.util.Arrays.asList;
import static org.jetbrains.idea.svn.SvnUtil.getRelativeUrl;
import static org.jetbrains.idea.svn.status.StatusType.*;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class SvnResolveTreeAcceptVariantsTest extends SvnTestCase {
  private VirtualFile myTheirs;
  private SvnClientRunnerImpl mySvnClientRunner;

  @Parameterized.Parameter
  public TreeConflictData.Data conflictData;
  @Parameterized.Parameter(1)
  public String conflictName;
  private FilePath conflictFile;
  private Change conflictChange;

  @Parameterized.Parameters(name = "{1}")
  public static Collection<Object[]> data() {
    return StreamEx.of(FileToFile.class, DirToDir.class, DirToFile.class, FileToDir.class)
      .flatMap(aClass -> Stream.of(aClass.getDeclaredFields()))
      .filter(field -> Modifier.isStatic(field.getModifiers()))
      // DirToDir.MINE_EDIT_THEIRS_DELETE - no more a conflict since 1.7.7
      .filter(field -> getStaticFieldValue(field) != DirToDir.MINE_EDIT_THEIRS_DELETE)
      .map(field -> ar(getStaticFieldValue(field), field.getDeclaringClass().getSimpleName() + "." + field.getName()))
      .toList();
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    mySvnClientRunner = new SvnClientRunnerImpl(myRunner);
    myTraceClient = true;

    setUpConflict();
  }

  private void setUpConflict() throws Exception {
    myTheirs = myTempDirFixture.findOrCreateDir("theirs");
    mySvnClientRunner.checkout(myRepoUrl, myTheirs);
    myTheirs.refresh(false, true);

    createSubTree();
    runInEdtAndWait(() -> new ConflictCreator(vcs, myTheirs, myWorkingCopyDir, conflictData, mySvnClientRunner).create());
    refreshChanges();

    boolean isDirectory = conflictName.startsWith("DirTo");
    conflictFile = getFilePath(new File(myWorkingCopyDir.getPath(), conflictData.getConflictFile()), isDirectory);
    conflictChange = changeListManager.getChange(conflictFile);
    assertNotNull(conflictChange);
    assertTrue(conflictChange instanceof ConflictedSvnChange);
  }

  @Test
  public void testMineFull() throws Exception {
    new SvnTreeConflictResolver(vcs, conflictFile, null).resolveSelectMineFull();
    myTheirs.refresh(false, true);
    refreshVfs();

    checkStatusesAfterMineFullResolve();
    checkFileContents();
  }

  @Test
  public void testTheirsFull() throws Exception {
    FilePath beforePath = conflictChange.isMoved() || conflictChange.isRenamed() ? conflictChange.getBeforeRevision().getFile() : null;

    new SvnTreeConflictResolver(vcs, conflictFile, beforePath).resolveSelectTheirsFull();
    myTheirs.refresh(false, true);
    refreshVfs();

    VfsUtil.processFileRecursivelyWithoutIgnored(myTheirs, file -> {
      final String relative = getRelativePath(file, myTheirs, File.separatorChar);
      File workingFile = new File(myWorkingCopyDir.getPath(), relative);
      boolean exists = workingFile.exists();
      if (!exists) {
        String[] excluded = conflictData.getExcludeFromToTheirsCheck();
        if (excluded != null && asList(excluded).contains(relative)) {
          return true;
        }
        assertTrue(createTestFailedComment(relative), exists);
      }
      final File theirsFile = virtualToIoFile(file);
      Info theirsInfo = vcs.getInfo(theirsFile);
      Info thisInfo = vcs.getInfo(workingFile);
      if (theirsInfo != null) {
        assertEquals(createTestFailedComment(relative) + ", theirs: " + theirsInfo.getRevision().getNumber() + ", mine: "
                     + thisInfo.getRevision().getNumber(), theirsInfo.getRevision().getNumber(), thisInfo.getRevision().getNumber());
        if (!theirsFile.isDirectory()) {
          try {
            assertEquals(FileUtil.loadFile(theirsFile), FileUtil.loadFile(workingFile));
          }
          catch (IOException e) {
            fail(e.getMessage());
          }
        }
      }
      return true;
    });
  }

  private void checkFileContents() throws IOException {
    for (TreeConflictData.FileData leftFile : conflictData.getLeftFiles()) {
      if (!leftFile.myIsDir && !StringUtil.isEmpty(leftFile.myContents)) {
        final File ioFile = new File(myWorkingCopyDir.getPath(), leftFile.myRelativePath);
        assertExists(ioFile);
        assertEquals(leftFile.myContents, FileUtil.loadFile(ioFile));
      }
    }
  }

  private void checkStatusesAfterMineFullResolve() {
    assertNull(createTestFailedComment(conflictFile.getPath()) + " tree conflict resolved",
               SvnUtil.getStatus(vcs, conflictFile.getIOFile()).getTreeConflict());
    for (TreeConflictData.FileData file : conflictData.getLeftFiles()) {
      File exFile = new File(myWorkingCopyDir.getPath(), file.myRelativePath);
      final Status status = SvnUtil.getStatus(vcs, exFile);
      boolean theirsExists = new File(myTheirs.getPath(), file.myRelativePath).exists();

      if (STATUS_UNVERSIONED.equals(file.myNodeStatus)) {
        assertTrue(createTestFailedComment(exFile.getPath()) + " (file exists)", exFile.exists());
        if (theirsExists) {
          // should be deleted
          assertTrue(createTestFailedComment(exFile.getPath()) + " (unversioned)", status == null || status.is(STATUS_DELETED));
        } else {
          // unversioned
          assertTrue(createTestFailedComment(exFile.getPath()) + " (unversioned)", status == null || status.is(STATUS_UNVERSIONED));
        }
      }
      else if (STATUS_DELETED.equals(file.myNodeStatus)) {
        assertTrue(createTestFailedComment(exFile.getPath()) + " (deleted status)", status != null && status.is(file.myNodeStatus));
      }
      else if (STATUS_ADDED.equals(file.myNodeStatus)) {
        assertTrue(createTestFailedComment(exFile.getPath()) + " (file exists)", exFile.exists());
        if (theirsExists) {
          assertTrue(createTestFailedComment(exFile.getPath()) + " (added status)", status != null && status.is(STATUS_REPLACED));
        } else {
          assertTrue(createTestFailedComment(exFile.getPath()) + " (added status)", status != null && status.is(STATUS_ADDED));
        }
      } else {
        if (status.is(STATUS_ADDED)) {
          // in theirs -> deleted
          assertFalse(createTestFailedComment(file.myRelativePath) + " check deleted in theirs", theirsExists);
        } else {
          if (theirsExists) {
            assertTrue(createTestFailedComment(exFile.getPath()) + " (normal node status)", status != null && status.is(STATUS_REPLACED));
          } else {
            assertTrue(createTestFailedComment(exFile.getPath()) + " (normal node status)",
                       status != null && status.is(STATUS_NORMAL, STATUS_MODIFIED));
          }
        }
        if (file.myCopyFrom != null) {
          assertTrue(createTestFailedComment(exFile.getPath()) + " (copied status)",
                     status != null && file.myCopyFrom.equals(getRelativeUrl(myRepositoryUrl, status.getCopyFromUrl())));
        }
        else {
          assertTrue(createTestFailedComment(exFile.getPath()) + " (modified text status)",
                     status != null && status.is(file.myContentsStatus));
        }
      }
    }
  }

  private String createTestFailedComment(final String path) {
    return "File: " + path + " in: " + myWorkingCopyDir.getPath();
  }

  private static Object getStaticFieldValue(Field field) {
    try {
      return field.get(null);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private void createSubTree() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    mySvnClientRunner.checkin(myWorkingCopyDir);
    new SubTree(myWorkingCopyDir);
    mySvnClientRunner.checkin(myWorkingCopyDir);
    mySvnClientRunner.update(myTheirs);
    mySvnClientRunner.update(myWorkingCopyDir);

    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }
}
