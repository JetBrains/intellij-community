package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.AbstractVcsTestCase;
import org.junit.Test;

/**
 * @author yole
 */
public class SvnRenameTest extends SvnTestCase {
  @Test
  public void simpleRename() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile a = createFileInCommand("a.txt", "test");
    AbstractVcsTestCase.RunResult result = runSvn("ci", "-m", "test");
    verify(result, "Adding\\s+a\\.txt");

    renameFileInCommand(a, "b.txt");
    result = runSvn("status");
    verify(result, "A\\s+\\+\\s+b.txt", "D\\s+a.txt");
  }
}