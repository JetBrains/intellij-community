// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.idea.svn.integrate.AlienDirtyScope;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author yole
 */
public class SvnDeleteTest extends SvnTestCase {
  // IDEADEV-16066
  @Test
  public void testDeletePackage() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    VirtualFile dir = createDirInCommand(myWorkingCopyDir, "child");
    createFileInCommand(dir, "a.txt", "content");

    runAndVerifyStatusSorted("A child", "A child/a.txt");
    checkin();

    deleteFileInCommand(dir);
    runAndVerifyStatusSorted("D child", "D child/a.txt");

    refreshVfs();

    final AlienDirtyScope dirtyScope = new AlienDirtyScope();
    dirtyScope.addDir(VcsUtil.getFilePath(myWorkingCopyDir));
    final List<Change> changesManually = getChangesInScope(dirtyScope);
    assertEquals(2, changesManually.size());

    refreshChanges();
    final List<LocalChangeList> lists = changeListManager.getChangeListsCopy();
    assertEquals(1, lists.size());
    final Collection<Change> changes = lists.get(0).getChanges();
    assertEquals(2, changes.size());
  }

  @Test
  public void testDeletePackageWhenVcsRemoveDisabled() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    VirtualFile dir = createDirInCommand(myWorkingCopyDir, "child");
    createFileInCommand(dir, "a.txt", "content");

    runAndVerifyStatusSorted("A child", "A child/a.txt");
    checkin();

    final File wasFile = virtualToIoFile(dir);
    deleteFileInCommand(dir);
    runAndVerifyStatusSorted("! child", "! child/a.txt");
    assertTrue(!wasFile.exists());
  }
}
