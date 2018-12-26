// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class SvnNativeListsTest extends SvnTestCase {
  @Override
  public void tearDown() throws Exception {
    final List<LocalChangeList> changeListList = changeListManager.getChangeLists();
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
    final LocalChangeList newL = changeListManager.addChangeList("newOne", null);
    refreshChanges();

    changeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    refreshChanges();
    ensureAddedToNativeList();

    runAndVerifyStatus("","--- Changelist 'newOne':", "A a.txt");
  }

  private void ensureAddedToNativeList() {
    refreshChanges();  // first time new changes are detected and added to _IDEA_ changeslist
    changeListManager.ensureUpToDate();  // and on the same thread a request is put for files addition;
    // so stay here for 2nd cycle and wait for native addition completion
  }

  @Test
  public void testDeleted() throws Throwable {
    final LocalChangeList newL = changeListManager.addChangeList("newOne", null);
    refreshChanges();

    changeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    refreshChanges();
    checkin();
    deleteFileInCommand(file);
    refreshChanges();
    ensureAddedToNativeList();

    runAndVerifyStatus("","--- Changelist 'newOne':", "D a.txt");
  }

  @Test
  public void testEdit() throws Throwable {
    final LocalChangeList newL = changeListManager.addChangeList("newOne", null);
    refreshChanges();

    changeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    refreshChanges();
    checkin();
    VcsTestUtil.editFileInCommand(myProject, file, "111");
    refreshChanges();
    ensureAddedToNativeList();

    runAndVerifyStatus("", "--- Changelist 'newOne':", "M a.txt");
  }

  @Test
  public void testEditAndMove() throws Throwable {
    final LocalChangeList newL = changeListManager.addChangeList("newOne", null);
    refreshChanges();

    changeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    refreshChanges();
    checkin();
    VcsTestUtil.editFileInCommand(myProject, file, "111");
    refreshChanges();
    ensureAddedToNativeList();

    runAndVerifyStatus("", "--- Changelist 'newOne':", "M a.txt");

    renameFileInCommand(file, "b.txt");
    refreshChanges();
    assertRename("a.txt", "b.txt");

    ensureAddedToNativeList();
    assertRename("a.txt", "b.txt");
  }

  @Test
  public void testMove() throws Throwable {
    final LocalChangeList newL = changeListManager.addChangeList("newOne", null);
    refreshChanges();

    changeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    refreshChanges();
    checkin();

    renameFileInCommand(file, "b.txt");
    refreshChanges();
    ensureAddedToNativeList();
    assertRename("a.txt", "b.txt");
  }

  @Test
  public void testMoveMove() throws Throwable {
    final LocalChangeList newL = changeListManager.addChangeList("newOne", null);
    refreshChanges();

    changeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    refreshChanges();
    checkin();

    renameFileInCommand(file, "b.txt");
    refreshChanges();
    ensureAddedToNativeList();
    assertRename("a.txt", "b.txt");

    renameFileInCommand(file, "c.txt");
    refreshChanges();
    assertRename("a.txt", "c.txt");

    ensureAddedToNativeList();
    assertRename("a.txt", "c.txt");
  }

  private void assertRename(@NotNull String beforeName, @NotNull String afterName) throws IOException {
    runAndVerifyStatus("", "--- Changelist 'newOne':", "D " + beforeName, "> moved to " + afterName, "A + " + afterName,
                       "> moved from " + beforeName);
  }
}
