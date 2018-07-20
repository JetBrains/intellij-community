// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.VcsAnnotationLocalChangesListener;
import com.intellij.openapi.vcs.history.VcsRevisionDescription;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.isEmpty;
import static java.util.Collections.singletonList;
import static org.junit.Assert.*;

public class SvnAnnotationIsClosedTest extends SvnTestCase {
  private volatile boolean myIsClosed;
  private volatile boolean myIsClosed1;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    myIsClosed = false;
    myIsClosed1 = false;
  }

  @Test
  public void testClosedByCommitFromIdea() throws Exception {
    final SubTree tree = setUpWorkingCopy();
    setUpAnnotation(tree.myS1File);

    editFileInCommand(tree.myS1File, "1\n2\n3**\n4++\n");
    assertFalse(myIsClosed);

    refreshChanges();

    final Change change = changeListManager.getChange(tree.myS1File);
    assertNotNull(change);
    final List<VcsException> exceptions = vcs.getCheckinEnvironment().commit(singletonList(change), "commit");
    assertTrue(isEmpty(exceptions));

    dirtyScopeManager.fileDirty(tree.myS1File);
    refreshChangesAndWait();
    assertTrue(myIsClosed);
  }

  @Test
  public void testClosedByUpdateInIdea() throws Exception {
    final SubTree tree = setUpWorkingCopy();
    runInAndVerifyIgnoreOutput("up", "-r", "2");
    setUpAnnotation(tree.myS1File);

    refreshChanges();

    imitUpdate();
    assertTrue(myIsClosed);
  }

  @Test
  public void testClosedChangedByUpdateInIdea() throws Exception {
    final SubTree tree = setUpWorkingCopy();
    runInAndVerifyIgnoreOutput("up", "-r", "2");
    setUpAnnotation(tree.myS1File);

    editFileInCommand(tree.myS1File, "1+\n2\n3\n4\n");
    refreshChanges();
    assertFalse(myIsClosed);

    imitUpdate();
    assertTrue(myIsClosed);
  }

  @Test
  public void testClosedByExternalUpdate() throws Exception {
    final SubTree tree = setUpWorkingCopy();
    runInAndVerifyIgnoreOutput("up", "-r", "2");
    setUpAnnotation(tree.myS1File);

    editFileInCommand(tree.myS1File, "1+\n2\n3\n4\n");
    refreshChanges();
    assertFalse(myIsClosed);

    update();
    refreshVfs();
    imitateEvent(myWorkingCopyDir);

    refreshChangesAndWait();
    assertTrue(myIsClosed);
  }

  @Test
  public void testNotClosedByRenaming() throws Exception {
    final SubTree tree = setUpWorkingCopy();
    setUpAnnotation(tree.myS1File);

    editFileInCommand(tree.myS1File, "1\n2\n3**\n4++\n");
    assertFalse(myIsClosed);

    renameFileInCommand(tree.myS1File, "5364536");
    assertFalse(myIsClosed);

    refreshChanges();
    assertNotNull(changeListManager.getChange(tree.myS1File));
  }

  @Test
  public void testAnnotateRenamed() throws Exception {
    final SubTree tree = setUpWorkingCopy();
    editFileInCommand(tree.myS1File, "1\n2\n3**\n4++\n");
    setUpAnnotation(tree.myS1File);
    assertFalse(myIsClosed);

    refreshChanges();
    assertNotNull(changeListManager.getChange(tree.myS1File));
  }

  @Test
  public void testClosedByExternalCommit() throws Exception {
    final SubTree tree = setUpWorkingCopy();
    setUpAnnotation(tree.myS1File);

    editFileInCommand(tree.myS1File, "1+\n2\n3\n4\n");
    refreshChanges();
    assertFalse(myIsClosed);

    checkin();
    refreshVfs();
    imitateEvent(myWorkingCopyDir);

    refreshChangesAndWait();
    assertTrue(myIsClosed);
  }

  @Test
  public void testClosedByUpdateWithExternals() throws Exception {
    prepareExternal();

    VirtualFile sourceDir = notNull(myWorkingCopyDir.findChild("source"));
    VirtualFile externalDir = notNull(sourceDir.findChild("external"));
    final VirtualFile vf1 = notNull(sourceDir.findChild("s1.txt"));
    final VirtualFile vf2 = notNull(externalDir.findChild("t12.txt"));
    editFileInCommand(vf1, "test externals 123" + System.currentTimeMillis());
    editFileInCommand(vf2, "test externals 123" + System.currentTimeMillis());

    refreshChanges();
    assertNotNull(changeListManager.getChange(vf1));
    assertNotNull(changeListManager.getChange(vf2));

    runInAndVerifyIgnoreOutput("ci", "-m", "test", sourceDir.getPath());
    runInAndVerifyIgnoreOutput("ci", "-m", "test", externalDir.getPath());

    editFileInCommand(vf2, "test externals 12344444" + System.currentTimeMillis());
    runInAndVerifyIgnoreOutput("ci", "-m", "test", externalDir.getPath());
    assertRevision(vf1, 3);
    assertRevision(vf2, 5);

    runInAndVerifyIgnoreOutput("up", "-r", "4", sourceDir.getPath());
    runInAndVerifyIgnoreOutput("up", "-r", "4", externalDir.getPath());
    assertRevision(vf1, 3);
    assertRevision(vf2, 4);

    setUpAnnotation(vf1);
    final VcsAnnotationLocalChangesListener listener = vcsManager.getAnnotationLocalChangesListener();
    final FileAnnotation annotation1 = createTestAnnotation(vcs.getAnnotationProvider(), vf2);
    annotation1.setCloser(() -> {
      myIsClosed1 = true;
      listener.unregisterAnnotation(vf1, annotation1);
    });
    listener.registerAnnotation(vf1, annotation1);

    runInAndVerifyIgnoreOutput("up", sourceDir.getPath());
    imitateEvent(sourceDir);
    imitateEvent(externalDir);
    refreshChangesAndWait();
    assertRevision(vf1, 3);
    assertRevision(vf2, 5);

    assertTrue(myIsClosed1);
    assertFalse(myIsClosed);
  }

  @NotNull
  private SubTree setUpWorkingCopy() throws IOException {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    editFileInCommand(tree.myS1File, "1\n2\n3\n4\n");
    checkin();
    editFileInCommand(tree.myS1File, "1\n2\n3**\n4\n");
    checkin();
    return tree;
  }

  private void setUpAnnotation(@NotNull VirtualFile file) throws VcsException {
    final VcsAnnotationLocalChangesListener listener = vcsManager.getAnnotationLocalChangesListener();
    final FileAnnotation annotation = createTestAnnotation(vcs.getAnnotationProvider(), file);
    annotation.setCloser(() -> {
      myIsClosed = true;
      listener.unregisterAnnotation(file, annotation);
    });
    listener.registerAnnotation(file, annotation);
  }

  private void refreshChangesAndWait() {
    changeListManager.ensureUpToDate(false);
    changeListManager.ensureUpToDate(false);  // wait for after-events like annotations recalculation
    sleep(100); // zipper updater
  }

  private void assertRevision(@NotNull VirtualFile file, final long number) {
    final VcsRevisionDescription revision = ((SvnDiffProvider)vcs.getDiffProvider()).getCurrentRevisionDescription(file);
    assertEquals(number, ((SvnRevisionNumber)revision.getRevisionNumber()).getLongRevisionNumber());
  }
}
