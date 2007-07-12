package org.jetbrains.idea.svn;

import org.junit.Test;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class SvnAddTest extends SvnTestCase {
  @Test
  public void testCopy() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile file = createFileInCommand("a.txt", "old content");
    checkin();
    copyFileInCommand(file, "b.txt");
    verify(runSvn("status"), "A + b.txt");
  }
}