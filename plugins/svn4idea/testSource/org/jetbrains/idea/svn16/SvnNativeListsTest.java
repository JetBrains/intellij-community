/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn16;

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.idea.svn.SvnChangelistListener;
import org.jetbrains.idea.svn.SvnVcs;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

public class SvnNativeListsTest extends Svn16TestCase {
  private ChangeListManager myChangeListManager;
  private VcsDirtyScopeManager myDirtyScopeManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
  }

  @Override
  public void tearDown() throws Exception {
    final List<LocalChangeList> changeListList = myChangeListManager.getChangeLists();
    for (LocalChangeList list : changeListList) {
      if (LocalChangeList.DEFAULT_NAME.equals(list.getName())) continue;
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
    SvnChangelistListener.removeFromList(SvnVcs.getInstance(myProject), revision.getFile().getIOFile());
  }

  @Test
  public void testAdd() throws Throwable {
    final LocalChangeList newL = myChangeListManager.addChangeList("newOne", null);
    sleepABit();
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    myChangeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    sleepABit();

    ensureAddedToNativeList();

    runAndVerifyStatus("","--- Changelist 'newOne':", "A a.txt");
  }

  private void ensureAddedToNativeList() {
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);  // first time new changes are detected and added to _IDEA_ changeslist
    myChangeListManager.ensureUpToDate(false);  // and on the same thread a request is put for files addition;
    // so stay here for 2nd cycle and wait for native addition completion
  }

  @Test
  public void testDeleted() throws Throwable {
    final LocalChangeList newL = myChangeListManager.addChangeList("newOne", null);
    sleepABit();
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    myChangeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    sleepABit();
    checkin();
    deleteFileInCommand(file);
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    sleepABit();
    ensureAddedToNativeList();

    runAndVerifyStatus("","--- Changelist 'newOne':", "D a.txt");
  }

  @Test
  public void testEdit() throws Throwable {
    final LocalChangeList newL = myChangeListManager.addChangeList("newOne", null);
    sleepABit();
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    myChangeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    sleepABit();
    checkin();
    VcsTestUtil.editFileInCommand(myProject, file, "111");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    sleepABit();
    ensureAddedToNativeList();

    runAndVerifyStatus("", "--- Changelist 'newOne':", "M a.txt");
  }

  @Test
  public void testEditAndMove() throws Throwable {
    final LocalChangeList newL = myChangeListManager.addChangeList("newOne", null);
    sleepABit();
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    myChangeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    sleepABit();
    checkin();
    VcsTestUtil.editFileInCommand(myProject, file, "111");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    sleepABit();
    ensureAddedToNativeList();

    runAndVerifyStatus("", "--- Changelist 'newOne':", "M a.txt");

    renameFileInCommand(file, "b.txt");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    sleepABit();
    runAndVerifyStatus("", "--- Changelist 'newOne':", "A + b.txt", "D a.txt");

    ensureAddedToNativeList();

    runAndVerifyStatus("", "--- Changelist 'newOne':", "A + b.txt", "D a.txt");
  }

  @Test
  public void testMove() throws Throwable {
    final LocalChangeList newL = myChangeListManager.addChangeList("newOne", null);
    sleepABit();
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    myChangeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    sleepABit();
    checkin();

    renameFileInCommand(file, "b.txt");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    sleepABit();
    ensureAddedToNativeList();

    runAndVerifyStatus("", "--- Changelist 'newOne':", "A + b.txt", "D a.txt");
  }

  @Test
  public void testMoveMove() throws Throwable {
    final LocalChangeList newL = myChangeListManager.addChangeList("newOne", null);
    sleepABit();
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    myChangeListManager.setDefaultChangeList(newL);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    sleepABit();
    checkin();

    renameFileInCommand(file, "b.txt");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    sleepABit();
    ensureAddedToNativeList();

    runAndVerifyStatus("", "--- Changelist 'newOne':", "A + b.txt", "D a.txt");

    renameFileInCommand(file, "c.txt");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    sleepABit();
    runAndVerifyStatus("", "--- Changelist 'newOne':", "A + c.txt", "D a.txt");

    ensureAddedToNativeList();

    runAndVerifyStatus("", "--- Changelist 'newOne':", "A + c.txt", "D a.txt");
  }

  private void sleepABit() {
    TimeoutUtil.sleep(50);
  }
}
