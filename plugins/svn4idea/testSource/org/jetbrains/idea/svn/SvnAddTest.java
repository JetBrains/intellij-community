package org.jetbrains.idea.svn;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

  // IDEADEV-16268
  @Test
  public void testDirAndFileInCommand() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        try {
          VirtualFile dir = myWorkingCopyDir.createChildDirectory(this, "child");
          dir.createChildData(this, "a.txt");
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }, "", null);
    final RunResult result = runSvn("status");
    verify(result, "A child", "A child\\a.txt");
  }

  // IDEADEV-19308
  @Test
  public void testDirAfterFile() throws Exception {
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile dir = createDirInCommand(myWorkingCopyDir, "dir");
    final VirtualFile file = createFileInCommand(dir, "a.txt", "content");
    
    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    files.add(file);
    files.add(dir);
    final List<VcsException> errors = SvnVcs.getInstance(myProject).getCheckinEnvironment().scheduleUnversionedFilesForAddition(files);
    Assert.assertEquals(0, errors.size());
  }
}