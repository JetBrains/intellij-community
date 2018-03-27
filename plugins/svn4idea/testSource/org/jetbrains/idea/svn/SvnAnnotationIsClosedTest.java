/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class SvnAnnotationIsClosedTest extends Svn17TestCase {
  private volatile boolean myIsClosed;
  private volatile boolean myIsClosed1;
  private SvnVcs myVcs;
  private ChangeListManager myChangeListManager;
  private VcsDirtyScopeManager myDirtyScopeManager;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    myVcs = SvnVcs.getInstance(myProject);
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
    final FileAnnotation annotation = createTestAnnotation(myVcs.getAnnotationProvider(), tree.myS1File);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(tree.myS1File, annotation);
    });
    listener.registerAnnotation(tree.myS1File, annotation);

    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3**\n4++\n");
    Assert.assertFalse(myIsClosed); // not closed on typing

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    final Change change = myChangeListManager.getChange(tree.myS1File);
    Assert.assertNotNull(change);

    final List<VcsException> exceptions = myVcs.getCheckinEnvironment().commit(Collections.singletonList(change), "commit");
    Assert.assertTrue(exceptions == null || exceptions.isEmpty());
    myDirtyScopeManager.fileDirty(tree.myS1File);

    myChangeListManager.ensureUpToDate(false);
    myChangeListManager.ensureUpToDate(false);  // wait for after-events like annotations recalculation
    sleep(100); // zipper updater
    Assert.assertTrue(myIsClosed);
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
    final FileAnnotation annotation = createTestAnnotation(myVcs.getAnnotationProvider(), tree.myS1File);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(tree.myS1File, annotation);
    });
    listener.registerAnnotation(tree.myS1File, annotation);

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    imitUpdate(myProject);
    Assert.assertTrue(myIsClosed);
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
    final FileAnnotation annotation = createTestAnnotation(myVcs.getAnnotationProvider(), tree.myS1File);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(tree.myS1File, annotation);
    });
    listener.registerAnnotation(tree.myS1File, annotation);
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1+\n2\n3\n4\n");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    Assert.assertFalse(myIsClosed);

    imitUpdate(myProject);
    Assert.assertTrue(myIsClosed);
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
    final FileAnnotation annotation = createTestAnnotation(myVcs.getAnnotationProvider(), tree.myS1File);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(tree.myS1File, annotation);
    });
    listener.registerAnnotation(tree.myS1File, annotation);
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1+\n2\n3\n4\n");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    Assert.assertFalse(myIsClosed);

    update();
    myWorkingCopyDir.refresh(false, true);
    imitateEvent(myWorkingCopyDir);

    myChangeListManager.ensureUpToDate(false);
    myChangeListManager.ensureUpToDate(false);  // wait for after-events like annotations recalculation
    sleep(100); // zipper updater
    Assert.assertTrue(myIsClosed);
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
    final FileAnnotation annotation = createTestAnnotation(myVcs.getAnnotationProvider(), tree.myS1File);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(tree.myS1File, annotation);
    });
    listener.registerAnnotation(tree.myS1File, annotation);

    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1\n2\n3**\n4++\n");
    Assert.assertFalse(myIsClosed); // not closed on typing
    VcsTestUtil.renameFileInCommand(myProject, tree.myS1File, "5364536");
    Assert.assertFalse(myIsClosed); // not closed on typing

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    final Change change = myChangeListManager.getChange(tree.myS1File);
    Assert.assertNotNull(change);
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
    final FileAnnotation annotation = createTestAnnotation(myVcs.getAnnotationProvider(), tree.myS1File);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(tree.myS1File, annotation);
    });
    listener.registerAnnotation(tree.myS1File, annotation);

    Assert.assertFalse(myIsClosed); // not closed on typing

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    final Change change = myChangeListManager.getChange(tree.myS1File);
    Assert.assertNotNull(change);
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
    final FileAnnotation annotation = createTestAnnotation(myVcs.getAnnotationProvider(), tree.myS1File);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(tree.myS1File, annotation);
    });
    listener.registerAnnotation(tree.myS1File, annotation);
    VcsTestUtil.editFileInCommand(myProject, tree.myS1File, "1+\n2\n3\n4\n");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    Assert.assertFalse(myIsClosed);

    checkin();
    myWorkingCopyDir.refresh(false, true);
    imitateEvent(myWorkingCopyDir);

    myChangeListManager.ensureUpToDate(false);
    myChangeListManager.ensureUpToDate(false);  // wait for after-events like annotations recalculation
    sleep(100); // zipper updater
    Assert.assertTrue(myIsClosed);
  }

  @Test
  public void testClosedByUpdateWithExternals() throws Exception {
    prepareExternal();

    final File sourceFile = new File(myWorkingCopyDir.getPath(), "source" + File.separator + "s1.txt");
    final File externalFile = new File(myWorkingCopyDir.getPath(), "source" + File.separator + "external" + File.separator + "t12.txt");

    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final VirtualFile vf1 = lfs.refreshAndFindFileByIoFile(sourceFile);
    final VirtualFile vf2 = lfs.refreshAndFindFileByIoFile(externalFile);

    Assert.assertNotNull(vf1);
    Assert.assertNotNull(vf2);

    VcsTestUtil.editFileInCommand(myProject, vf1, "test externals 123" + System.currentTimeMillis());
    VcsTestUtil.editFileInCommand(myProject, vf2, "test externals 123" + System.currentTimeMillis());

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change change1 = myChangeListManager.getChange(vf1);
    final Change change2 = myChangeListManager.getChange(vf2);
    Assert.assertNotNull(change1);
    Assert.assertNotNull(change2);

    final File sourceDir = new File(myWorkingCopyDir.getPath(), "source");
    final File externalDir = new File(myWorkingCopyDir.getPath(), "source/external");
    runInAndVerifyIgnoreOutput("ci", "-m", "test", sourceDir.getPath());   // #3
    runInAndVerifyIgnoreOutput("ci", "-m", "test", externalDir.getPath()); // #4

    VcsTestUtil.editFileInCommand(myProject, vf2, "test externals 12344444" + System.currentTimeMillis());
    runInAndVerifyIgnoreOutput("ci", "-m", "test", externalDir.getPath()); // #5

    final SvnDiffProvider diffProvider = (SvnDiffProvider) myVcs.getDiffProvider();

    assertRevision(vf1, diffProvider, 3);
    assertRevision(vf2, diffProvider, 5);

    runInAndVerifyIgnoreOutput("up", "-r", "4", sourceDir.getPath());
    runInAndVerifyIgnoreOutput("up", "-r", "4", externalDir.getPath());

    assertRevision(vf1, diffProvider, 3);
    assertRevision(vf2, diffProvider, 4);

    // then annotate both
    final VcsAnnotationLocalChangesListener listener = ProjectLevelVcsManager.getInstance(myProject).getAnnotationLocalChangesListener();
    final FileAnnotation annotation = createTestAnnotation(myVcs.getAnnotationProvider(), vf1);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(vf1, annotation);
    });
    listener.registerAnnotation(vf1, annotation);

    final FileAnnotation annotation1 = createTestAnnotation(myVcs.getAnnotationProvider(), vf2);
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

    Assert.assertTrue(myIsClosed1);
    Assert.assertFalse(myIsClosed);  // in source is not closed..
  }

  private void assertRevision(VirtualFile vf1, SvnDiffProvider diffProvider, final long number) {
    final VcsRevisionDescription vf1Rev = diffProvider.getCurrentRevisionDescription(vf1);
    Assert.assertEquals(number, ((SvnRevisionNumber) vf1Rev.getRevisionNumber()).getLongRevisionNumber());
  }

  // test is not closed by ext/int update to not-last-committed-rev (rev that changes another file) +
  // test with externals +
  // test closed by external commit +
  // test closed by external update +
  // test closed by internal update +
  // test not closed by typing +
  // test not closed by renaming +
}
