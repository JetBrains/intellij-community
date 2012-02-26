package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.integrate.AlienDirtyScope;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.List;

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

    verify(runSvn("status"), "A child", "A child" + File.separatorChar + "a.txt");
    checkin();

    deleteFileInCommand(dir);
    verify(runSvn("status"), "D child", "D child" + File.separatorChar + "a.txt");

    LocalFileSystem.getInstance().refresh(false);

    final AlienDirtyScope dirtyScope = new AlienDirtyScope();
    dirtyScope.addDir(new FilePathImpl(myWorkingCopyDir));
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
}
