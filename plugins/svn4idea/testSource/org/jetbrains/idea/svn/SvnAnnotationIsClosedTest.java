// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsAnnotationLocalChangesListener;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.history.VcsRevisionDescription;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class SvnAnnotationIsClosedTest extends SvnTestCase {
  private volatile boolean myIsClosed;
  private volatile boolean myIsClosed1;
  private ChangeListManager myChangeListManager;
  private VcsDirtyScopeManager myDirtyScopeManager;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    myIsClosed = false;
    myIsClosed1 = false;
  }

  @Test
  public void testClosedByCommitFromIdea() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n");
    checkin();
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3**\n4\n");
    checkin();

    final VcsAnnotationLocalChangesListener listener = ProjectLevelVcsManager.getInstance(myProject).getAnnotationLocalChangesListener();
    final FileAnnotation annotation = createTestAnnotation(vcs.getAnnotationProvider(), tree.myS1File);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(tree.myS1File, annotation);
    });
    listener.registerAnnotation(tree.myS1File, annotation);

    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3**\n4++\n");
    assertFalse(myIsClosed); // not closed on typing

    refreshChanges();
    final Change change = myChangeListManager.getChange(tree.myS1File);
    assertNotNull(change);

    final List<VcsException> exceptions = vcs.getCheckinEnvironment().commit(Collections.singletonList(change), "commit");
    assertTrue(exceptions == null || exceptions.isEmpty());
    myDirtyScopeManager.fileDirty(tree.myS1File);

    myChangeListManager.ensureUpToDate(false);
    myChangeListManager.ensureUpToDate(false);  // wait for after-events like annotations recalculation
    sleep(100); // zipper updater
    assertTrue(myIsClosed);
  }

  @Test
  public void testClosedByUpdateInIdea() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();  //#1
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n");
    checkin();  //#2
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3**\n4\n");
    checkin();  //#3
    runInAndVerifyIgnoreOutput("up", "-r", "2");

    final VcsAnnotationLocalChangesListener listener = ProjectLevelVcsManager.getInstance(myProject).getAnnotationLocalChangesListener();
    final FileAnnotation annotation = createTestAnnotation(vcs.getAnnotationProvider(), tree.myS1File);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(tree.myS1File, annotation);
    });
    listener.registerAnnotation(tree.myS1File, annotation);

    refreshChanges();

    imitUpdate(myProject);
    assertTrue(myIsClosed);
  }

  @Test
  public void testClosedChangedByUpdateInIdea() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();  //#1
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n");
    checkin();  //#2
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3**\n4\n");
    checkin();  //#3
    runInAndVerifyIgnoreOutput("up", "-r", "2");  // take #2

    final VcsAnnotationLocalChangesListener listener = ProjectLevelVcsManager.getInstance(myProject).getAnnotationLocalChangesListener();
    final FileAnnotation annotation = createTestAnnotation(vcs.getAnnotationProvider(), tree.myS1File);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(tree.myS1File, annotation);
    });
    listener.registerAnnotation(tree.myS1File, annotation);
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1+\n2\n3\n4\n");

    refreshChanges();
    assertFalse(myIsClosed);

    imitUpdate(myProject);
    assertTrue(myIsClosed);
  }

  @Test
  public void testClosedByExternalUpdate() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();  //#1
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n");
    checkin();  //#2
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3**\n4\n");
    checkin();  //#3
    runInAndVerifyIgnoreOutput("up", "-r", "2");  // take #2

    final VcsAnnotationLocalChangesListener listener = ProjectLevelVcsManager.getInstance(myProject).getAnnotationLocalChangesListener();
    final FileAnnotation annotation = createTestAnnotation(vcs.getAnnotationProvider(), tree.myS1File);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(tree.myS1File, annotation);
    });
    listener.registerAnnotation(tree.myS1File, annotation);
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1+\n2\n3\n4\n");

    refreshChanges();
    assertFalse(myIsClosed);

    update();
    myWorkingCopyDir.refresh(false, true);
    imitateEvent(myWorkingCopyDir);

    myChangeListManager.ensureUpToDate(false);
    myChangeListManager.ensureUpToDate(false);  // wait for after-events like annotations recalculation
    sleep(100); // zipper updater
    assertTrue(myIsClosed);
  }

  @Test
  public void testNotClosedByRenaming() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n");
    checkin();
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3**\n4\n");
    checkin();

    final VcsAnnotationLocalChangesListener listener = ProjectLevelVcsManager.getInstance(myProject).getAnnotationLocalChangesListener();
    final FileAnnotation annotation = createTestAnnotation(vcs.getAnnotationProvider(), tree.myS1File);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(tree.myS1File, annotation);
    });
    listener.registerAnnotation(tree.myS1File, annotation);

    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3**\n4++\n");
    assertFalse(myIsClosed); // not closed on typing
    VcsTestUtil.renameFileInCommand(myProject, tree.myS1File, "5364536");
    assertFalse(myIsClosed); // not closed on typing

    refreshChanges();
    final Change change = myChangeListManager.getChange(tree.myS1File);
    assertNotNull(change);
  }

  @Test
  public void testAnnotateRenamed() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n");
    checkin();
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3**\n4\n");
    checkin();
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3**\n4++\n");

    final VcsAnnotationLocalChangesListener listener = ProjectLevelVcsManager.getInstance(myProject).getAnnotationLocalChangesListener();
    final FileAnnotation annotation = createTestAnnotation(vcs.getAnnotationProvider(), tree.myS1File);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(tree.myS1File, annotation);
    });
    listener.registerAnnotation(tree.myS1File, annotation);

    assertFalse(myIsClosed); // not closed on typing

    refreshChanges();
    final Change change = myChangeListManager.getChange(tree.myS1File);
    assertNotNull(change);
  }

  @Test
  public void testClosedByExternalCommit() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();  //#1
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3\n4\n");
    checkin();  //#2
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3**\n4\n");
    checkin();  //#3

    final VcsAnnotationLocalChangesListener listener = ProjectLevelVcsManager.getInstance(myProject).getAnnotationLocalChangesListener();
    final FileAnnotation annotation = createTestAnnotation(vcs.getAnnotationProvider(), tree.myS1File);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(tree.myS1File, annotation);
    });
    listener.registerAnnotation(tree.myS1File, annotation);
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1+\n2\n3\n4\n");

    refreshChanges();
    assertFalse(myIsClosed);

    checkin();
    myWorkingCopyDir.refresh(false, true);
    imitateEvent(myWorkingCopyDir);

    myChangeListManager.ensureUpToDate(false);
    myChangeListManager.ensureUpToDate(false);  // wait for after-events like annotations recalculation
    sleep(100); // zipper updater
    assertTrue(myIsClosed);
  }

  @Test
  public void testClosedByUpdateWithExternals() throws Exception {
    prepareExternal();

    final File sourceFile = new File(myWorkingCopyDir.getPath(), "source" + File.separator + "s1.txt");
    final File externalFile = new File(myWorkingCopyDir.getPath(), "source" + File.separator + "external" + File.separator + "t12.txt");

    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final VirtualFile vf1 = lfs.refreshAndFindFileByIoFile(sourceFile);
    final VirtualFile vf2 = lfs.refreshAndFindFileByIoFile(externalFile);

    assertNotNull(vf1);
    assertNotNull(vf2);

    VcsTestUtil.editFileInCommand(myProject, vf1, "test externals 123" + System.currentTimeMillis());
    VcsTestUtil.editFileInCommand(myProject, vf2, "test externals 123" + System.currentTimeMillis());

    refreshChanges();

    final Change change1 = myChangeListManager.getChange(vf1);
    final Change change2 = myChangeListManager.getChange(vf2);
    assertNotNull(change1);
    assertNotNull(change2);

    final File sourceDir = new File(myWorkingCopyDir.getPath(), "source");
    final File externalDir = new File(myWorkingCopyDir.getPath(), "source/external");
    runInAndVerifyIgnoreOutput("ci", "-m", "test", sourceDir.getPath());   // #3
    runInAndVerifyIgnoreOutput("ci", "-m", "test", externalDir.getPath()); // #4

    VcsTestUtil.editFileInCommand(myProject, vf2, "test externals 12344444" + System.currentTimeMillis());
    runInAndVerifyIgnoreOutput("ci", "-m", "test", externalDir.getPath()); // #5

    final SvnDiffProvider diffProvider = (SvnDiffProvider)vcs.getDiffProvider();

    assertRevision(vf1, diffProvider, 3);
    assertRevision(vf2, diffProvider, 5);

    runInAndVerifyIgnoreOutput("up", "-r", "4", sourceDir.getPath());
    runInAndVerifyIgnoreOutput("up", "-r", "4", externalDir.getPath());

    assertRevision(vf1, diffProvider, 3);
    assertRevision(vf2, diffProvider, 4);

    // then annotate both
    final VcsAnnotationLocalChangesListener listener = ProjectLevelVcsManager.getInstance(myProject).getAnnotationLocalChangesListener();
    final FileAnnotation annotation = createTestAnnotation(vcs.getAnnotationProvider(), vf1);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(vf1, annotation);
    });
    listener.registerAnnotation(vf1, annotation);

    final FileAnnotation annotation1 = createTestAnnotation(vcs.getAnnotationProvider(), vf2);
    annotation1.setCloser(() -> {
      myIsClosed1 = true;
      listener.unregisterAnnotation(vf1, annotation1);
    });
    listener.registerAnnotation(vf1, annotation1);

    //up
    runInAndVerifyIgnoreOutput("up", sourceDir.getPath());
    imitateEvent(lfs.refreshAndFindFileByIoFile(sourceDir));
    imitateEvent(lfs.refreshAndFindFileByIoFile(externalDir));
    myChangeListManager.ensureUpToDate(false);
    myChangeListManager.ensureUpToDate(false);  // wait for after-events like annotations recalculation
    sleep(100); // zipper updater
    //verify(runSvn("up", "-r", "3", externalDir.getPath()));
    assertRevision(vf1, diffProvider, 3);
    assertRevision(vf2, diffProvider, 5);

    assertTrue(myIsClosed1);
    assertFalse(myIsClosed);  // in source is not closed..
  }

  private void assertRevision(VirtualFile vf1, SvnDiffProvider diffProvider, final long number) {
    final VcsRevisionDescription vf1Rev = diffProvider.getCurrentRevisionDescription(vf1);
    assertEquals(number, ((SvnRevisionNumber)vf1Rev.getRevisionNumber()).getLongRevisionNumber());
  }

  // test is not closed by ext/int update to not-last-committed-rev (rev that changes another file) +
  // test with externals +
  // test closed by external commit +
  // test closed by external update +
  // test closed by internal update +
  // test not closed by typing +
  // test not closed by renaming +
}
