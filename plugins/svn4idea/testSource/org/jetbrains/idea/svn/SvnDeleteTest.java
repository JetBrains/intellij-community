// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.idea.svn.integrate.AlienDirtyScope;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

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

    runAndVerifyStatusSorted("A child", "A child" + File.separatorChar + "a.txt");
    checkin();

    deleteFileInCommand(dir);
    runAndVerifyStatusSorted("D child", "D child" + File.separatorChar + "a.txt");

    refreshVfs();

    final AlienDirtyScope dirtyScope = new AlienDirtyScope();
    dirtyScope.addDir(VcsUtil.getFilePath(myWorkingCopyDir));
    final List<Change> changesManually = getChangesInScope(dirtyScope);
    Assert.assertEquals(2, changesManually.size());

    refreshChanges();
    final List<LocalChangeList> lists = changeListManager.getChangeListsCopy();
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

    final File wasFile = virtualToIoFile(dir);
    deleteFileInCommand(dir);
    runAndVerifyStatusSorted("! child", "! child" + File.separatorChar + "a.txt");
    Assert.assertTrue(! wasFile.exists());
  }
}
