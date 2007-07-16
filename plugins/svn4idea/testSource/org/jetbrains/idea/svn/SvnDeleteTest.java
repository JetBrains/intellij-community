package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.testFramework.vcs.MockChangelistBuilder;
import org.junit.Test;
import org.junit.Assert;

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

    verify(runSvn("status"), "A child", "A child\\a.txt");
    checkin();

    deleteFileInCommand(dir);
    verify(runSvn("status"), "D child", "D child\\a.txt");

    LocalFileSystem.getInstance().refresh(false);

    ChangeProvider changeProvider = SvnVcs.getInstance(myProject).getChangeProvider();
    assert changeProvider != null;
    MockChangelistBuilder builder = new MockChangelistBuilder();
    VcsDirtyScope dirtyScope = getAllDirtyScope();
    changeProvider.getChanges(dirtyScope, builder, new EmptyProgressIndicator());
    Assert.assertEquals(2, builder.getChanges().size());
    
  }
}