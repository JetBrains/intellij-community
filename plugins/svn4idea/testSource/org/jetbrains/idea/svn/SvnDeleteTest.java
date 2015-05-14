/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.idea.svn.integrate.AlienDirtyScope;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class SvnDeleteTest extends Svn17TestCase {
  // IDEADEV-16066
  @Test
  public void testDeletePackage() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    VirtualFile dir = createDirInCommand(myWorkingCopyDir, "child");
    createFileInCommand(dir, "a.txt", "content");

    runAndVerifyStatusSorted("A child", "A child" + File.separatorChar + "a.txt");
    checkin();

    deleteFileInCommand(dir);
    runAndVerifyStatusSorted("D child", "D child" + File.separatorChar + "a.txt");

    refreshVfs();

    final AlienDirtyScope dirtyScope = new AlienDirtyScope();
    dirtyScope.addDir(VcsUtil.getFilePath(myWorkingCopyDir));
    final List<Change> changesManually = getChangesInScope(dirtyScope);
    Assert.assertEquals(2, changesManually.size());

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    // since ChangeListManager is runnning, it can take dirty scope itself;... it's easier to just take changes from it
    final ChangeListManager clManager = ChangeListManager.getInstance(myProject);
    clManager.ensureUpToDate(false);
    final List<LocalChangeList> lists = clManager.getChangeListsCopy();
    Assert.assertEquals(1, lists.size());
    final Collection<Change> changes = lists.get(0).getChanges();
    Assert.assertEquals(2, changes.size());
  }

  @Test
  public void testDeletePackageWhenVcsRemoveDisabled() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    VirtualFile dir = createDirInCommand(myWorkingCopyDir, "child");
    createFileInCommand(dir, "a.txt", "content");

    runAndVerifyStatusSorted("A child", "A child" + File.separatorChar + "a.txt");
    checkin();

    final File wasFile = new File(dir.getPath());
    deleteFileInCommand(dir);
    runAndVerifyStatusSorted("! child", "! child" + File.separatorChar + "a.txt");
    Assert.assertTrue(! wasFile.exists());
  }
}
