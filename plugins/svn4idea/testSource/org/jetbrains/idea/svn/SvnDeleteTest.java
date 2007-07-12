package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author yole
 */
public class SvnDeleteTest extends SvnTestCase {
  // IDEADEV-16066
  @Test
  @Ignore
  public void testDeletePackage() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    VirtualFile dir = createDirInCommand(myWorkingCopyDir, "child");
    createFileInCommand(dir, "a.txt", "content");

    verify(runSvn("status"), "A child", "A child\\a.txt");
    checkin();

    deleteFileInCommand(dir);
    verify(runSvn("status"), "D child", "D child\\a.txt");
  }
}