package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;
import org.junit.Ignore;

import java.io.IOException;

/**
 * @author yole
 */
public class SvnRenameTest extends SvnTestCase {
  @Test
  public void testSimpleRename() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile a = createFileInCommand("a.txt", "test");
    checkin();

    renameFileInCommand(a, "b.txt");
    verify(runSvn("status"), "A + b.txt", "D a.txt");
  }

  private void checkin() throws IOException {
    verify(runSvn("ci", "-m", "test"));
  }

  // IDEADEV-18844
  @Test
  @Ignore
  public void testRenameReplace() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile a = createFileInCommand("a.txt", "old");
    final VirtualFile aNew = createFileInCommand("aNew.txt", "new");
    checkin();

    renameFileInCommand(a, "aOld.txt");
    renameFileInCommand(aNew, "a.txt");
    verify(runSvn("status"), "R + a.txt", "D aNew.txt", "A + aOld.txt");
  }
}