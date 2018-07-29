// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;
import org.jetbrains.idea.svn.treeConflict.SvnTreeConflictResolver;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static com.intellij.openapi.vfs.VfsUtilCore.getRelativePath;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.UsefulTestCase.assertExists;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.jetbrains.idea.svn.SvnUtil.isAdminDirectory;
import static org.junit.Assert.*;

public class SvnResolveTreeAcceptVariantsTest extends SvnTestCase {
  private VirtualFile myTheirs;
  private SvnClientRunnerImpl mySvnClientRunner;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);

    myTheirs = myTempDirFixture.findOrCreateDir("theirs");
    mySvnClientRunner = new SvnClientRunnerImpl(myRunner);
    clearWc(true);

    myTraceClient = true;
  }

  private void clearWc(final boolean withSvn) throws Exception {
    refreshVfs();
    clearDirInCommand(myWorkingCopyDir, file -> withSvn || !isAdminDirectory(file));
    refreshVfs();
  }

  @Test
  public void testMineFull() throws Exception {
    int cnt = 0;
    for (final TreeConflictData.Data data : TreeConflictData.ourAll) {
      if (myTraceClient) {
        System.out.println("========= TEST " + getTestName(data) + " =========");
      }

      changeListManager.stopEveryThingIfInTestMode();
      myWorkingCopyDir = createDirInCommand(myWorkingCopyDir, "test" + cnt);
      myTheirs = createDirInCommand(myTheirs, "theirs" + cnt);
      mySvnClientRunner.checkout(myRepoUrl, myTheirs);
      mySvnClientRunner.checkout(myRepoUrl, myWorkingCopyDir);

      vcsManager.setDirectoryMappings(singletonList(new VcsDirectoryMapping(myWorkingCopyDir.getPath(), vcs.getName())));
      createSubTree();
      myTheirs.refresh(false, true);
      runInEdtAndWait(() -> new ConflictCreator(vcs, myTheirs, myWorkingCopyDir, data, mySvnClientRunner).create());

      changeListManager.forceGoInTestMode();
      refreshChanges();
      refreshChanges();

      final String conflictFile = data.getConflictFile();
      final File conflictIoFile = new File(myWorkingCopyDir.getPath(), conflictFile);
      final FilePath filePath = VcsUtil.getFilePath(conflictIoFile);
      final Change change = changeListManager.getChange(filePath);
      assertNotNull(change);
      assertTrue(change instanceof ConflictedSvnChange);

      new SvnTreeConflictResolver(vcs, filePath, null).resolveSelectMineFull();

      myTheirs.refresh(false, true);
      refreshVfs();
      checkStatusesAfterMineFullResolve(data, conflictIoFile);
      checkFileContents(data);

      ++ cnt;
    }
  }

  private void checkFileContents(TreeConflictData.Data data) throws IOException {
    for (TreeConflictData.FileData leftFile : data.getLeftFiles()) {
      if (!leftFile.myIsDir && !StringUtil.isEmpty(leftFile.myContents)) {
        final File ioFile = new File(myWorkingCopyDir.getPath(), leftFile.myRelativePath);
        assertExists(ioFile);
        assertEquals(leftFile.myContents, FileUtil.loadFile(ioFile));
      }
    }
  }

  private void checkStatusesAfterMineFullResolve(TreeConflictData.Data data, File conflictIoFile) {
    assertNull(createTestFailedComment(data, conflictIoFile.getPath()) + " tree conflict resolved",
               SvnUtil.getStatus(vcs, conflictIoFile).getTreeConflict());
    for (TreeConflictData.FileData file : data.getLeftFiles()) {
      File exFile = new File(myWorkingCopyDir.getPath(), file.myRelativePath);
      final Status status = SvnUtil.getStatus(vcs, exFile);
      boolean theirsExists = new File(myTheirs.getPath(), file.myRelativePath).exists();

      if (StatusType.STATUS_UNVERSIONED.equals(file.myNodeStatus)) {
        assertTrue(createTestFailedComment(data, exFile.getPath()) + " (file exists)", exFile.exists());
        if (theirsExists) {
          // should be deleted
          assertTrue(createTestFailedComment(data, exFile.getPath()) + " (unversioned)",
                     status == null || StatusType.STATUS_DELETED.equals(status.getNodeStatus()));
        } else {
          // unversioned
          assertTrue(createTestFailedComment(data, exFile.getPath()) + " (unversioned)",
                     status == null || StatusType.STATUS_UNVERSIONED.equals(status.getNodeStatus()));
        }
      } else if (StatusType.STATUS_DELETED.equals(file.myNodeStatus)) {
        assertTrue(createTestFailedComment(data, exFile.getPath()) + " (deleted status)",
                   status != null && file.myNodeStatus.equals(status.getNodeStatus()));
      } else if (StatusType.STATUS_ADDED.equals(file.myNodeStatus)) {
        assertTrue(createTestFailedComment(data, exFile.getPath()) + " (file exists)", exFile.exists());
        if (theirsExists) {
          assertTrue(createTestFailedComment(data, exFile.getPath()) + " (added status)",
                     status != null && StatusType.STATUS_REPLACED.equals(status.getNodeStatus()));
        } else {
          assertTrue(createTestFailedComment(data, exFile.getPath()) + " (added status)",
                     status != null && StatusType.STATUS_ADDED.equals(status.getNodeStatus()));
        }
      } else {
        if (StatusType.STATUS_ADDED.equals(status.getNodeStatus())) {
          // in theirs -> deleted
          assertFalse(createTestFailedComment(data, file.myRelativePath) + " check deleted in theirs", theirsExists);
        } else {
          if (theirsExists) {
            assertTrue(createTestFailedComment(data, exFile.getPath()) + " (normal node status)",
                       status != null && StatusType.STATUS_REPLACED.equals(status.getNodeStatus()));
          } else {
            assertTrue(createTestFailedComment(data, exFile.getPath()) + " (normal node status)",
                       status != null &&
                       (StatusType.STATUS_NORMAL.equals(status.getNodeStatus()) ||
                        StatusType.STATUS_MODIFIED.equals(status.getNodeStatus())));
          }
        }
        assertTrue(createTestFailedComment(data, exFile.getPath()) + " (modified text status)",
                   status != null && file.myContentsStatus.equals(status.getContentsStatus()));
      }
    }
  }

  private String createTestFailedComment(final TreeConflictData.Data data, final String path) {
    return "Check failed for test: " + getTestName(data) + " and file: " + path + " in: " + myWorkingCopyDir.getPath();
  }

  @Test
  public void testTheirsFull() throws Exception {
    int cnt = 0;
    for (final TreeConflictData.Data data : TreeConflictData.ourAll) {
      if (myTraceClient) {
        System.out.println("========= TEST " + getTestName(data) + " =========");
      }

      myWorkingCopyDir = createDirInCommand(myWorkingCopyDir, "test" + cnt);
      myTheirs = createDirInCommand(myTheirs, "theirs" + cnt);
      mySvnClientRunner.checkout(myRepoUrl, myTheirs);
      mySvnClientRunner.checkout(myRepoUrl, myWorkingCopyDir);

      createSubTree();
      runInEdtAndWait(() -> new ConflictCreator(vcs, myTheirs, myWorkingCopyDir, data, mySvnClientRunner).create());

      refreshChanges();
      refreshChanges();

      final String conflictFile = data.getConflictFile();
      final File conflictIoFile = new File(myWorkingCopyDir.getPath(), conflictFile);
      final FilePath filePath = VcsUtil.getFilePath(conflictIoFile);
      final Change change = changeListManager.getChange(filePath);
      assertNotNull(change);
      assertTrue(change instanceof ConflictedSvnChange);
      FilePath beforePath = null;
      if (change.isMoved() || change.isRenamed()) {
        beforePath = change.getBeforeRevision().getFile();
      }

      new SvnTreeConflictResolver(vcs, filePath, beforePath).resolveSelectTheirsFull();

      myTheirs.refresh(false, true);
      refreshVfs();
      VfsUtil.processFileRecursivelyWithoutIgnored(myTheirs, file -> {
        final String relative = getRelativePath(file, myTheirs, File.separatorChar);
        File workingFile = new File(myWorkingCopyDir.getPath(), relative);
        boolean exists = workingFile.exists();
        if (! exists) {
          String[] excluded = data.getExcludeFromToTheirsCheck();
          if (excluded != null && asList(excluded).contains(relative)) {
            return true;
          }
          assertTrue(createTestFailedComment(data, relative), exists);
        }
        final File theirsFile = virtualToIoFile(file);
        Info theirsInfo = vcs.getInfo(theirsFile);
        Info thisInfo = vcs.getInfo(workingFile);
        if (theirsInfo != null) {
          assertEquals(createTestFailedComment(data, relative) + ", theirs: " + theirsInfo.getRevision().getNumber() + ", mine: "
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
      ++ cnt;
    }
  }

  private static String getTestName(final TreeConflictData.Data data) {
    Class<?>[] classes = TreeConflictData.class.getDeclaredClasses();
    for (Class<?> aClass : classes) {
      String s = testFields(data, aClass);
      if (s != null) return aClass.getName() + "#" + s;
    }
    return null;
  }

  private static String testFields(TreeConflictData.Data data, final Class clazz) {
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      int modifiers = field.getModifiers();
      try {
        if (Modifier.isStatic(modifiers) && data == field.get(null)) {
          return field.getName();
        }
      }
      catch (IllegalAccessException e) {
        e.printStackTrace();
        return null;
      }
    }
    return null;
  }

  private void createSubTree() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    clearWc(false);
    mySvnClientRunner.checkin(myWorkingCopyDir);
    new SubTree(myWorkingCopyDir);
    mySvnClientRunner.checkin(myWorkingCopyDir);
    mySvnClientRunner.update(myTheirs);
    mySvnClientRunner.update(myWorkingCopyDir);

    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }
}
