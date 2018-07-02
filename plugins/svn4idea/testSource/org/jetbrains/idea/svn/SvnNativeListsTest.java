// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.TimeoutUtil;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

public class SvnNativeListsTest extends SvnTestCase {
  private ChangeListManager myChangeListManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myChangeListManager = ChangeListManager.getInstance(myProject);
  }

  @Override
  public void tearDown() throws Exception {
    final List<LocalChangeList> changeListList = myChangeListManager.getChangeLists();
    for (LocalChangeList list : changeListList) {
      if (list.hasDefaultName()) continue;
      final Collection<Change> changes = list.getChanges();
      for (Change change : changes) {
        clearListForRevision(change.getBeforeRevision());
        clearListForRevision(change.getAfterRevision());
      }
    }

    super.tearDown();
  }

  private void clearListForRevision(final ContentRevision revision) throws VcsException {
    if (revision == null) return;
    SvnChangelistListener.removeFromList(vcs, revision.getFile().getIOFile());
  }

  @Test
  public void testAdd() throws Throwable {
    final LocalChangeList newL = myChangeListManager.addChangeList("newOne", null);
    sleepABit();
    refreshChanges();

    myChangeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    refreshChanges();
    sleepABit();

    ensureAddedToNativeList();

    runAndVerifyStatus("","--- Changelist 'newOne':", "A a.txt");
  }

  private void ensureAddedToNativeList() {
    refreshChanges();  // first time new changes are detected and added to _IDEA_ changeslist
    myChangeListManager.ensureUpToDate(false);  // and on the same thread a request is put for files addition;
    // so stay here for 2nd cycle and wait for native addition completion
  }

  @Test
  public void testDeleted() throws Throwable {
    final LocalChangeList newL = myChangeListManager.addChangeList("newOne", null);
    sleepABit();
    refreshChanges();

    myChangeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    refreshChanges();
    sleepABit();
    checkin();
    deleteFileInCommand(file);
    refreshChanges();
    sleepABit();
    ensureAddedToNativeList();

    runAndVerifyStatus("","--- Changelist 'newOne':", "D a.txt");
  }

  @Test
  public void testEdit() throws Throwable {
    final LocalChangeList newL = myChangeListManager.addChangeList("newOne", null);
    sleepABit();
    refreshChanges();

    myChangeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    refreshChanges();
    sleepABit();
    checkin();
    VcsTestUtil.editFileInCommand(myProject, file, "111");
    refreshChanges();
    sleepABit();
    ensureAddedToNativeList();

    runAndVerifyStatus("", "--- Changelist 'newOne':", "M a.txt");
  }

  @Test
  public void testEditAndMove() throws Throwable {
    final LocalChangeList newL = myChangeListManager.addChangeList("newOne", null);
    sleepABit();
    refreshChanges();

    myChangeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    refreshChanges();
    sleepABit();
    checkin();
    VcsTestUtil.editFileInCommand(myProject, file, "111");
    refreshChanges();
    sleepABit();
    ensureAddedToNativeList();

    runAndVerifyStatus("", "--- Changelist 'newOne':", "M a.txt");

    renameFileInCommand(file, "b.txt");
    refreshChanges();
    sleepABit();
    runAndVerifyStatus("", "--- Changelist 'newOne':", "A + b.txt", "D a.txt");

    ensureAddedToNativeList();

    runAndVerifyStatus("", "--- Changelist 'newOne':", "A + b.txt", "D a.txt");
  }

  @Test
  public void testMove() throws Throwable {
    final LocalChangeList newL = myChangeListManager.addChangeList("newOne", null);
    sleepABit();
    refreshChanges();

    myChangeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    refreshChanges();
    sleepABit();
    checkin();

    renameFileInCommand(file, "b.txt");
    refreshChanges();
    sleepABit();
    ensureAddedToNativeList();

    runAndVerifyStatus("", "--- Changelist 'newOne':", "A + b.txt", "D a.txt");
  }

  @Test
  public void testMoveMove() throws Throwable {
    final LocalChangeList newL = myChangeListManager.addChangeList("newOne", null);
    sleepABit();
    refreshChanges();

    myChangeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    refreshChanges();
    sleepABit();
    checkin();

    renameFileInCommand(file, "b.txt");
    refreshChanges();
    sleepABit();
    ensureAddedToNativeList();

    runAndVerifyStatus("", "--- Changelist 'newOne':", "A + b.txt", "D a.txt");

    renameFileInCommand(file, "c.txt");
    refreshChanges();
    sleepABit();
    runAndVerifyStatus("", "--- Changelist 'newOne':", "A + c.txt", "D a.txt");

    ensureAddedToNativeList();

    runAndVerifyStatus("", "--- Changelist 'newOne':", "A + c.txt", "D a.txt");
  }

  private void sleepABit() {
    TimeoutUtil.sleep(50);
  }
}
